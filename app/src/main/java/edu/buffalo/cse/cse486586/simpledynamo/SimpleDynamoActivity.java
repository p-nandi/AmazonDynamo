package edu.buffalo.cse.cse486586.simpledynamo;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SimpleDynamoActivity extends Activity {

    Button lDump = null;
    Button gDump = null;
    Uri providerUri;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_simple_dynamo);
        providerUri = Uri.parse("content://" + "edu.buffalo.cse.cse486586.simpledynamo.provider"
                + "/edu.buffalo.cse.cse486586.simpledynamo.SimpleDynamotProvider");

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button3).setOnClickListener(
                new OnTestClickListener(tv, getContentResolver()));
        lDump = (Button)findViewById(R.id.button1);
        gDump = (Button)findViewById(R.id.button2);


        lDump.setOnClickListener(new Button.OnClickListener(){
            TextView tv = (TextView) findViewById(R.id.textView1);
            public void onClick(View view){
                Cursor resultCursor = getContentResolver().query(providerUri, null,
                        CommonConstants.QUERY_LOCAL, null, null);
                tv.setText("");
                int keyIndex = resultCursor.getColumnIndex(CommonConstants.KEY_FIELD);
                int valueIndex = resultCursor.getColumnIndex(CommonConstants.VALUE_FIELD);
                resultCursor.moveToFirst();
                while(resultCursor.isAfterLast()==false){
                    String returnKey = resultCursor.getString(keyIndex);
                    String returnValue = resultCursor.getString(valueIndex);
                    //tv.append(returnKey+"$"+returnValue);
                    tv.append(returnKey);
                    tv.append("\n");
                    resultCursor.moveToNext();
                }
                resultCursor.close();

            }
        });
        gDump.setOnClickListener(new Button.OnClickListener(){
            TextView tv = (TextView) findViewById(R.id.textView1);
            public void onClick(View view){
                Cursor resultCursor = getContentResolver().query(providerUri, null,
                        CommonConstants.QUERY_GLOBAL, null, null);
                tv.setText("");
                int keyIndex = resultCursor.getColumnIndex(CommonConstants.KEY_FIELD);
                int valueIndex = resultCursor.getColumnIndex(CommonConstants.VALUE_FIELD);
                resultCursor.moveToFirst();
                while(resultCursor.isAfterLast()==false){
                    String returnKey = resultCursor.getString(keyIndex);
                    String returnValue = resultCursor.getString(valueIndex);
                    //tv.append(returnKey+"$"+returnValue);
                    tv.append(returnKey);
                    tv.append("\n");
                    resultCursor.moveToNext();
                }
                resultCursor.close();

            }
        });

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.simple_dynamo, menu);
		return true;
	}
	
	public void onStop() {
        super.onStop();
	    Log.v("Test", "onStop()");
	}

}
