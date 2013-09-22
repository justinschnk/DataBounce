package com.example.android.wifidirect.discovery;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends Activity {

    private Account accounts[];
    QuickContactBadge badge;
    ProgressBar progressSpinner;
    Account user;
    String username;
    Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_view);
        badge = (QuickContactBadge) findViewById(R.id.quickbadge);
        progressSpinner = (ProgressBar) findViewById(R.id.progressspinner);
        badge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                return;
            }
        });

        //Start Crazy Photo Trick
        TelephonyManager tMgr = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        String mPhoneNumber = tMgr.getLine1Number();
        String contactId = fetchContactIdFromPhoneNumber(mPhoneNumber);
        Uri uri = getPhotoUri(Long.parseLong(contactId));
        badge.assignContactUri(uri);
        final Bitmap bitmap = loadContactPhoto(getContentResolver(), Long.parseLong(contactId));
        badge.setImageBitmap(getCroppedBitmap(bitmap));
        //End Crazy Photo Trick
//        badge.setImageURI(Uri.parse(ContactsContract.Profile.PHOTO_URI));
//
//        badge.assignContactUri(ContactsContract.Profile.CONTENT_URI);
        AccountManager am = AccountManager.get(this);
        accounts = am.getAccountsByType("com.google");
        List<String> accountList = new ArrayList<String>();
        for (int i = 0; i < accounts.length; i++) {
            accountList.add(accounts[i].name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_selectable_list_item, android.R.id.text1, accountList);
        ListView accountListView = (ListView) findViewById(R.id.accountlist);
        accountListView.setAdapter(adapter);
        accountListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                user = accounts[position];
                username = user.name.substring(0,user.name.indexOf("@"));
                progressSpinner.setVisibility(View.VISIBLE);
                // new CheckInternet().execute(null, null, null);

                intent = new Intent(getApplicationContext(), WiFiServiceDiscoveryActivity.class);
                intent.putExtra("username", username);
                intent.putExtra("badge", bitmap);
                //Check Internet by making a "new profile" everytime (since this doesn't actually hurt existing profiles
                new RegisterNewUser().execute();
//                startActivity(i);
            }
        });

    }

    public Bitmap getCroppedBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        // canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
                bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        //Bitmap _bmp = Bitmap.createScaledBitmap(output, 60, 60, false);
        //return _bmp;
        return output;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }


    private class CheckInternet extends AsyncTask<URL, Integer, Long> {
        String feedback = "";
        int statusCode;

        @Override
        protected Long doInBackground(URL... params) {
            DefaultHttpClient httpclient = new DefaultHttpClient();
            HttpGet httpget = new HttpGet("http://ec2-54-242-29-177.compute-1.amazonaws.com:5000/users/" + username + "/");
            try {
                HttpResponse response = httpclient.execute(httpget);
                String responseContent = EntityUtils.toString(response.getEntity());
                //statusCode = response.getStatusLine().getStatusCode();
                if (responseContent!=null && !responseContent.equals("")) {
                    if (responseContent.contains("\"Error\": \"User with name ")) {
                        //Server doesn't have user registered
                        new RegisterNewUser().execute();

                    } else {
                        //Server has user
                        Intent i = new Intent(getApplicationContext(), ProfileActivity.class);
                        i.putExtra("username", username);
                        startActivity(i);
                    }
                } else {
                    //Server is Down or no internet
                    Intent i = new Intent(getApplicationContext(), WiFiServiceDiscoveryActivity.class);
                    i.putExtra("username", username);
                    startActivity(i);
                }

            } catch (IOException e) {
                e.printStackTrace();
                Intent i = new Intent(getApplicationContext(), WiFiServiceDiscoveryActivity.class);
                i.putExtra("username", username);
                startActivity(i);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Long aLong) {
            super.onPostExecute(aLong);
            progressSpinner.setVisibility(View.INVISIBLE);
//            Toast.makeText(getApplicationContext(), feedback, Toast.LENGTH_SHORT).show();
        }
    }

    private class RegisterNewUser extends AsyncTask {
        @Override
        protected Void doInBackground(Object... params) {
            DefaultHttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost("http://ec2-54-242-29-177.compute-1.amazonaws.com:5000/users/");
            httppost.getParams().setParameter("name",username);
            httppost.getParams().setParameter("email",user.name);
            httppost.getParams().setParameter("is_proxy","false");
            HttpResponse response = null;
            try {
                response = httpclient.execute(httppost);
                //int statusCode = response.getStatusLine().getStatusCode();
                String responseContent = EntityUtils.toString(response.getEntity());
                if (responseContent != null && !responseContent.equals("")) {   //&& !responseContent.contains("\"Error\": \"User with name ")
                    intent.putExtra("internet",true);
                } else {
                    intent.putExtra("internet",false);
                }
            } catch (IOException e) {
                e.printStackTrace();
                intent.putExtra("internet",false);
            }
            startActivity(intent);
            return null;
        }


    }

    private String fetchContactIdFromPhoneNumber(String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber));
        Cursor cursor = this.getContentResolver().query(uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup._ID},
                null, null, null);

        String contactId = "";

        if (cursor.moveToFirst()) {
            do {
                contactId = cursor.getString(cursor
                        .getColumnIndex(ContactsContract.PhoneLookup._ID));
            } while (cursor.moveToNext());
        }

        return contactId;
    }

    private static Bitmap loadContactPhoto(ContentResolver cr, long id) {
        Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
        InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(cr, uri);
        if (input == null) {
            return null;
        }
        return BitmapFactory.decodeStream(input);
    }

    private Uri getPhotoUri(long contactId) {
        ContentResolver contentResolver = getContentResolver();

        try {
            Cursor cursor = contentResolver
                    .query(ContactsContract.Data.CONTENT_URI,
                            null,
                            ContactsContract.Data.CONTACT_ID
                                    + "="
                                    + contactId
                                    + " AND "

                                    + ContactsContract.Data.MIMETYPE
                                    + "='"
                                    + ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                                    + "'", null, null);

            if (cursor != null) {
                if (!cursor.moveToFirst()) {
                    return null; // no photo
                }
            } else {
                return null; // error in cursor process
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        Uri person = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI, contactId);
        return Uri.withAppendedPath(person,
                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
    }

}
