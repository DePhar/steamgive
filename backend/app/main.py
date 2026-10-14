from __future__ import annotations

import secrets
from datetime import datetime, timedelta, timezone
from typing import AsyncGenerator

import jwt
from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from pwdlib import PasswordHash
from pydantic import BaseModel, ConfigDict, EmailStr, Field
from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Settings(BaseSettings):
    database_url: str
    jwt_secret: str = Field(min_length=32)
    access_token_minutes: int = Field(default=60, ge=5, le=1440)
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
engine = create_async_engine(settings.database_url, pool_pre_ping=True)
SessionLocal = async_sessionmaker(engine, expire_on_commit=False)
password_hash = PasswordHash.recommended()
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    email: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    username: Mapped[str] = mapped_column(String(32), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    points: Mapped[int] = mapped_column(Integer, default=100)
    is_admin: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))


class Giveaway(Base):
    __tablename__ = "giveaways"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    title: Mapped[str] = mapped_column(String(120))
    description: Mapped[str] = mapped_column(Text, default="")
    prize_name: Mapped[str] = mapped_column(String(160))
    cost_points: Mapped[int] = mapped_column(Integer, default=1)
    winner_count: Mapped[int] = mapped_column(Integer, default=1)
    creator_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    ends_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))
    drawn_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class Entry(Base):
    __tablename__ = "entries"
    __table_args__ = (UniqueConstraint("giveaway_id", "user_id", name="uq_entry_giveaway_user"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    giveaway_id: Mapped[int] = mapped_column(ForeignKey("giveaways.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))


class Ledger(Base):
    __tablename__ = "ledger"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    amount: Mapped[int] = mapped_column(Integer)
    reason: Mapped[str] = mapped_column(String(80))
    giveaway_id: Mapped[int | None] = mapped_column(ForeignKey("giveaways.id"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))


class RegisterRequest(BaseModel):
    email: EmailStr
    username: str = Field(min_length=3, max_length=32, pattern=r"^[a-zA-Z0-9_]+$")
    password: str = Field(min_length=10, max_length=128)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=128)


class UserOut(BaseModel):
    id: int
    email: EmailStr
    username: str
    points: int
    is_admin: bool
    model_config = ConfigDict(from_attributes=True)


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"


class GiveawayCreate(BaseModel):
    title: str = Field(min_length=3, max_length=120)
    description: str = Field(default="", max_length=5000)
    prize_name: str = Field(min_length=2, max_length=160)
    cost_points: int = Field(default=1, ge=1, le=10000)
    winner_count: int = Field(default=1, ge=1, le=100)
    ends_at: datetime


class GiveawayOut(BaseModel):
    id: int
    title: str
    description: str
    prize_name: str
    cost_points: int
    winner_count: int
    creator_id: int
    ends_at: datetime
    created_at: datetime
    drawn_at: datetime | None
    model_config = ConfigDict(from_attributes=True)


class DrawOut(BaseModel):
    giveaway_id: int
    winner_user_ids: list[int]
    drawn_at: datetime


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with SessionLocal() as session:
        yield session


def make_token(user_id: int) -> str:
    now = datetime.now(timezone.utc)
    return jwt.encode(
        {"sub": str(user_id), "iat": now, "exp": now + timedelta(minutes=settings.access_token_minutes)},
        settings.jwt_secret,
        algorithm="HS256",
    )


async def get_current_user(token: str = Depends(oauth2_scheme), db: AsyncSession = Depends(get_db)) -> User:
    invalid = HTTPException(status_code=401, detail="Invalid or expired token", headers={"WWW-Authenticate": "Bearer"})
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
        user_id = int(payload["sub"])
    except (jwt.PyJWTError, KeyError, TypeError, ValueError):
        raise invalid
    user = await db.get(User, user_id)
    if user is None:
        raise invalid
    return user


async def create_schema() -> None:
    # Development bootstrap only. Use Alembic migrations before production.
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)


app = FastAPI(title="GiftHunter API", version="0.1.0", description="Android-first giveaway platform prototype")


@app.on_event("startup")
async def startup() -> None:
    await create_schema()


@app.on_event("shutdown")
async def shutdown() -> None:
    await engine.dispose()


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/auth/register", response_model=UserOut, status_code=201)
async def register(payload: RegisterRequest, db: AsyncSession = Depends(get_db)) -> User:
    email = str(payload.email).lower()
    existing = await db.scalar(select(User).where((User.email == email) | (User.username == payload.username)))
    if existing:
        raise HTTPException(status_code=409, detail="Email or username is already registered")
    user = User(email=email, username=payload.username, password_hash=password_hash.hash(payload.password), points=100)
    db.add(user)
    try:
        await db.flush()
        db.add(Ledger(user_id=user.id, amount=100, reason="welcome_bonus"))
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=409, detail="Email or username is already registered")
    await db.refresh(user)
    return user


@app.post("/auth/login", response_model=TokenOut)
async def login(payload: LoginRequest, db: AsyncSession = Depends(get_db)) -> TokenOut:
    user = await db.scalar(select(User).where(User.email == str(payload.email).lower()))
    if user is None or not password_hash.verify(payload.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Incorrect email or password", headers={"WWW-Authenticate": "Bearer"})
    return TokenOut(access_token=make_token(user.id))


@app.get("/users/me", response_model=UserOut)
async def me(user: User = Depends(get_current_user)) -> User:
    return user


@app.get("/giveaways", response_model=list[GiveawayOut])
async def list_giveaways(limit: int = 30, offset: int = 0, db: AsyncSession = Depends(get_db)) -> list[Giveaway]:
    if not 1 <= limit <= 100 or offset < 0:
        raise HTTPException(status_code=422, detail="limit must be 1..100 and offset must be non-negative")
    result = await db.scalars(select(Giveaway).order_by(Giveaway.created_at.desc()).limit(limit).offset(offset))
    return list(result.all())


@app.post("/giveaways", response_model=GiveawayOut, status_code=201)
async def create_giveaway(payload: GiveawayCreate, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)) -> Giveaway:
    if payload.ends_at.tzinfo is None:
        raise HTTPException(status_code=422, detail="ends_at must include a timezone")
    if payload.ends_at <= datetime.now(timezone.utc):
        raise HTTPException(status_code=422, detail="ends_at must be in the future")
    giveaway = Giveaway(title=payload.title, description=payload.description, prize_name=payload.prize_name,
                        cost_points=payload.cost_points, winner_count=payload.winner_count,
                        creator_id=user.id, ends_at=payload.ends_at)
    db.add(giveaway)
    await db.commit()
    await db.refresh(giveaway)
    return giveaway


@app.post("/giveaways/{giveaway_id}/enter", status_code=201)
async def enter_giveaway(giveaway_id: int, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)) -> dict[str, str]:
    locked_user = await db.scalar(select(User).where(User.id == user.id).with_for_update())
    giveaway = await db.scalar(select(Giveaway).where(Giveaway.id == giveaway_id).with_for_update())
    if locked_user is None or giveaway is None:
        raise HTTPException(status_code=404, detail="User or giveaway not found")
    if giveaway.drawn_at is not None or giveaway.ends_at <= datetime.now(timezone.utc):
        raise HTTPException(status_code=409, detail="Giveaway is closed")
    if giveaway.creator_id == locked_user.id:
        raise HTTPException(status_code=409, detail="Creators cannot enter their own giveaway")
    existing = await db.scalar(select(Entry.id).where(Entry.giveaway_id == giveaway_id, Entry.user_id == locked_user.id))
    if existing is not None:
        raise HTTPException(status_code=409, detail="Already entered")
    if locked_user.points < giveaway.cost_points:
        raise HTTPException(status_code=409, detail="Not enough points")
    locked_user.points -= giveaway.cost_points
    db.add(Entry(giveaway_id=giveaway.id, user_id=locked_user.id))
    db.add(Ledger(user_id=locked_user.id, amount=-giveaway.cost_points, reason="giveaway_entry", giveaway_id=giveaway.id))
    await db.commit()
    return {"status": "entered"}


@app.post("/giveaways/{giveaway_id}/draw", response_model=DrawOut)
async def draw_winners(giveaway_id: int, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)) -> DrawOut:
    giveaway = await db.scalar(select(Giveaway).where(Giveaway.id == giveaway_id).with_for_update())
    if giveaway is None:
        raise HTTPException(status_code=404, detail="Giveaway not found")
    if giveaway.creator_id != user.id and not user.is_admin:
        raise HTTPException(status_code=403, detail="Only the creator or an admin can draw winners")
    if giveaway.drawn_at is not None:
        raise HTTPException(status_code=409, detail="Winners have already been drawn")
    if giveaway.ends_at > datetime.now(timezone.utc):
        raise HTTPException(status_code=409, detail="Giveaway has not ended yet")
    result = await db.scalars(select(Entry.user_id).where(Entry.giveaway_id == giveaway_id))
    entrants = list(result.all())
    winners = secrets.SystemRandom().sample(entrants, min(giveaway.winner_count, len(entrants)))
    giveaway.drawn_at = datetime.now(timezone.utc)
    await db.commit()
    return DrawOut(giveaway_id=giveaway.id, winner_user_ids=winners, drawn_at=giveaway.drawn_at)
