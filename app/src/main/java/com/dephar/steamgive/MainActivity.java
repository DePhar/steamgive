package com.dephar.steamgive;

import android.app.*; import android.os.*; import android.graphics.Color; import android.graphics.Typeface; import android.view.*; import android.widget.*; import java.util.*;

public class MainActivity extends Activity {
  LinearLayout root, content; int blue=Color.rgb(102,192,244); int bg=Color.rgb(11,13,18); int card=Color.rgb(21,26,34);
  String[] titles={"Cyberpunk 2077","Hades II","Balatro"}; String[] values={"$59.99","$29.99","$14.99"}; String[] entries={"842","314","197"}; String[] times={"03:42:18","05:17:03","08:09:44"};
  public void onCreate(Bundle b){super.onCreate(b); showHome();}
  TextView tv(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(20,14,20,14);return t;}
  Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);return b;}
  void base(){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL); ScrollView sv=new ScrollView(this);sv.addView(content);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1)); LinearLayout nav=new LinearLayout(this);String[] ns={"⌂ Home","♥ Wishlist","＋ Create","● Profile"};for(int i=0;i<4;i++){Button n=btn(ns[i]);final int x=i;n.setOnClickListener(v->{if(x==0)showHome();if(x==1)showWishlist();if(x==2)showCreate();if(x==3)showProfile();});nav.addView(n,new LinearLayout.LayoutParams(0,64,1));}root.addView(nav);setContentView(root);}
  void showHome(){base();content.addView(tv("SteamGive",28));content.addView(tv("Free games. Fair chances.",15));content.addView(tv("🔥 Recommended",21));for(int i=0;i<3;i++){final int x=i;LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(8,8,8,8);c.setBackgroundColor(card);TextView a=tv(titles[i],20);a.setTypeface(null,Typeface.BOLD);c.addView(a);c.addView(tv(values[i]+"   •   "+entries[i]+" entries   •   ⏱ "+times[i],14));Button e=btn("🎁 Enter giveaway");e.setOnClickListener(v->detail(x));c.addView(e);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(20,10,20,10);content.addView(c,p);}}
  void detail(int i){base();content.addView(tv("‹  Giveaway",22));content.addView(tv(titles[i],29));content.addView(tv(values[i]+"   •   "+entries[i]+" participants",16));content.addView(tv("About this giveaway\n\nPrototype giveaway. Production version will connect Steam, real entries, points and winner selection.",17));Button b=btn("🎁  Enter giveaway");b.setOnClickListener(v->{b.setText("✓  You are entered");});content.addView(b);}
  void showWishlist(){base();content.addView(tv("♥ Wishlist",28));content.addView(tv("Games you want to watch for giveaways.",16));content.addView(tv("\nYour wishlist is empty",20));}
  void showCreate(){base();content.addView(tv("🎁 Create giveaway",28));EditText e=new EditText(this);e.setHint("Steam App ID");e.setTextColor(Color.WHITE);e.setHintTextColor(Color.GRAY);content.addView(e);Button b=btn("Create giveaway");content.addView(b);}
  void showProfile(){base();content.addView(tv("●  Guest User",28));content.addView(tv("Steam account not connected\n\nLevel     0\nPoints    0\nWon       0",17));content.addView(btn("Connect Steam"));}
}
