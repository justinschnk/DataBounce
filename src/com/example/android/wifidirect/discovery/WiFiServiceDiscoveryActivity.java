
package com.example.android.wifidirect.discovery;

import android.app.Activity;
import android.app.Fragment;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdTxtRecordListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.webkit.WebView;
import android.widget.*;

import com.example.android.wifidirect.discovery.WiFiChatFragment.MessageTarget;
import com.example.android.wifidirect.discovery.WiFiDirectServicesList.DeviceClickListener;
import com.example.android.wifidirect.discovery.WiFiDirectServicesList.WiFiDevicesAdapter;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The main activity for the sample. This activity registers a local service and
 * perform discovery over Wi-Fi p2p network. It also hosts a couple of fragments
 * to manage chat operations. When the app is launched, the device publishes a
 * chat service and also tries to discover services published by other peers. On
 * selecting a peer published service, the app initiates a Wi-Fi P2P (Direct)
 * connection with the peer. On successful connection with a peer advertising
 * the same service, the app opens up sockets to initiate a chat.
 * {@code WiFiChatFragment} is then added to the the main activity which manages
 * the interface and messaging needs for a chat session.
 */
public class WiFiServiceDiscoveryActivity extends Activity implements
        DeviceClickListener, Handler.Callback, MessageTarget,
        ConnectionInfoListener {

    public static final String TAG = "wifidirectdemo";

    // TXT RECORD properties
    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    public static final String SERVICE_INSTANCE = "_wifidemotest";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";

    public static final int MESSAGE_READ = 0x400 + 1;
    public static final int MY_HANDLE = 0x400 + 2;
    private WifiP2pManager manager;

    private int connectedToHex = 0;

    static final int SERVER_PORT = 4545;

    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;
    private WifiP2pDnsSdServiceRequest serviceRequest;

    private Handler handler = new Handler(this);
    private WiFiChatFragment chatFragment;
    private WiFiDirectServicesList servicesList;

    private TextView statusTxtView;

    EditText searchEditText;
    RelativeLayout mMainHex;
    RelativeLayout mPeerHex1;
    RelativeLayout mPeerHex2;
    RelativeLayout mPeerHex3;
    RelativeLayout mPeerHex4;
    ImageView mMainHexImg;
    RotateAnimation rotate;
    RotateAnimation rotatePeer;

    TextView profileTextView;
    ImageView profileImageView;
    GridLayout profileLayout;


    Bitmap badge;
    String username;
    boolean internet;

    private final static int INITIAL_STATE = 0;
    private final static int SEARCH_STATE = 1;
    int mState = INITIAL_STATE;

    public Handler getHandler() {
        return handler;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        statusTxtView = (TextView) findViewById(R.id.status_text);


        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter
                .addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter
                .addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        startRegistrationAndDiscovery();

        servicesList = new WiFiDirectServicesList();
        getFragmentManager().beginTransaction()
                .add(R.id.container_root, servicesList, "services").commit();


        searchEditText = ((EditText)findViewById(R.id.search));


        mMainHex = (RelativeLayout) findViewById(R.id.main_hex_rel);
        mMainHexImg = (ImageView) findViewById(R.id.main_hex);

        rotate = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.setDuration(1000);
        rotate.setRepeatCount(Animation.INFINITE);
        mMainHexImg.startAnimation(rotate);

        mPeerHex1 = (RelativeLayout)findViewById(R.id.main_hex_rel1);
        mPeerHex2 = (RelativeLayout)findViewById(R.id.main_hex_rel2);
        mPeerHex3 = (RelativeLayout)findViewById(R.id.main_hex_rel3);
        mPeerHex4 = (RelativeLayout)findViewById(R.id.main_hex_rel4);


        badge = (Bitmap) getIntent().getParcelableExtra("badge");
        Log.d(TAG, "bitmap is "+badge);
        
        username = getIntent().getStringExtra("username");



        internet = getIntent().getBooleanExtra("internet",false);

        profileTextView = (TextView) findViewById(R.id.profile_title);
        profileTextView.setText(username);
        profileImageView = (ImageView) findViewById(R.id.profile_image);
        profileImageView.setImageBitmap(getCroppedBitmap(badge));
        profileLayout = (GridLayout) findViewById(R.id.profile_layout);



        //if(internet){
            profileLayout.setVisibility(View.VISIBLE);
            profileLayout.bringToFront();
        //}
    }

    public void hexClicked(View v) {
        // Move the hex to the center
        if (mState == INITIAL_STATE) {
            mMainHexImg.setImageBitmap(getCroppedBitmap(badge));
            mMainHexImg.setAnimation(null);
            ((TextView)findViewById(R.id.internet_txt)).setVisibility(View.INVISIBLE);
            Animation centerAnim = AnimationUtils.loadAnimation(this, R.anim.center);
            v.startAnimation(centerAnim);
            v.setClickable(false);
            ((LinearLayout)findViewById(R.id.search_layout)).setVisibility(View.VISIBLE);
            mState = SEARCH_STATE;
            updateNeighborHexes();
        }
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

    ArrayList<WiFiP2pService> serviceArrayList = new ArrayList<WiFiP2pService>();
    public void addNeighborHex(WiFiP2pService service) {
        if (!serviceArrayList.contains(service)) {
            serviceArrayList.add(service);
        }

        Log.d(TAG, "in addNeighbor. State is "+mState);
        if (mState == SEARCH_STATE) {
            updateNeighborHexes();
        }
    }

    private void updateNeighborHexes() {
        hideAllHexes();
        for (int i=0; i<serviceArrayList.size(); i++) {
            WiFiP2pService service = serviceArrayList.get(i);
            showHex(i+1, service);
        }
    }

    private void showHex(int id, WiFiP2pService service) {


        Log.d(TAG, id+": "+service.device.deviceName);
        // Log.d(TAG, "photo: "+getPhotoUriFromEmail(service.device.deviceName+"@gmail.com"));



        ImageView thisHexPerson = null;
        ImageView thisHex = null;

        if (id==1 || id==3) {
            rotatePeer = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        } else {
            rotatePeer = new RotateAnimation(360, 0, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        }
        rotatePeer.setDuration(20000);
        rotatePeer.setInterpolator(new LinearInterpolator());
        rotatePeer.setRepeatCount(Animation.INFINITE);

        if (id==1) {
            mPeerHex1.setVisibility(View.VISIBLE);
            thisHexPerson = ((ImageView)findViewById(R.id.main_hex1_person));
            thisHex = ((ImageView)findViewById(R.id.main_hex1));
        } else if (id==2) {
            mPeerHex2.setVisibility(View.VISIBLE);
            thisHexPerson = ((ImageView)findViewById(R.id.main_hex2_person));
            thisHex = ((ImageView)findViewById(R.id.main_hex2));
        } else if (id==3) {
            mPeerHex3.setVisibility(View.VISIBLE);
            thisHexPerson = ((ImageView)findViewById(R.id.main_hex3_person));
            thisHex = ((ImageView)findViewById(R.id.main_hex3));
        } else if (id==4) {
            mPeerHex4.setVisibility(View.VISIBLE);
            thisHexPerson = ((ImageView)findViewById(R.id.main_hex4_person));
            thisHex = ((ImageView)findViewById(R.id.main_hex4));
        }

        if (thisHex != null) {
            thisHex.setBackgroundResource(R.drawable.hexnone);
            thisHex.startAnimation(rotatePeer);
        }

        if (thisHexPerson != null) {
            thisHexPerson.setImageResource(R.drawable.person);

        }


    }

    public Uri getContactPhotoUri(long contactId) {
        Uri photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
        photoUri = Uri.withAppendedPath(photoUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
        return photoUri;
    }

    public void getPhotoFromEmail(String email) {


    }

    private void hideAllHexes() {
        mPeerHex1.setVisibility(View.INVISIBLE);
        mPeerHex2.setVisibility(View.INVISIBLE);
        mPeerHex3.setVisibility(View.INVISIBLE);
        mPeerHex4.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onRestart() {
        Fragment frag = getFragmentManager().findFragmentByTag("services");
        if (frag != null) {
            getFragmentManager().beginTransaction().remove(frag).commit();
        }
        super.onRestart();
    }

    @Override
    protected void onStop() {
        if (manager != null && channel != null) {
            manager.removeGroup(channel, new ActionListener() {

                @Override
                public void onFailure(int reasonCode) {
                    Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
                }

                @Override
                public void onSuccess() {
                }

            });
        }
        super.onStop();
    }


    String searchQuery = "_";
    public void searchClicked(View v) {
        searchQuery = searchEditText.getText().toString();
        Log.d(TAG, "searching "+searchQuery);

         connectToNextPeer();
    }

    public void connectToNextPeer() {
        try {
            View v = null;
            Animation a = null;
            if (connectedToHex == 0) {
                v = findViewById(R.id.main_hex1);
                a = findViewById(R.id.main_hex1).getAnimation();
            }
            if (connectedToHex == 1) {
                v = findViewById(R.id.main_hex2);
                a = findViewById(R.id.main_hex2).getAnimation();
            }
            if (connectedToHex == 2) {
                v = findViewById(R.id.main_hex3);
                a = findViewById(R.id.main_hex3).getAnimation();
            }
            if (connectedToHex == 3) {
                v = findViewById(R.id.main_hex4);
                a = findViewById(R.id.main_hex4).getAnimation();
            }

            if (a != null && v!=null) {
                v.setAnimation(null);
                a.setDuration(1000);
                v.startAnimation(a);
            }


            servicesList.onListItemClick(servicesList.getListView(), null, connectedToHex++, -1);
            if (connectedToHex>3) {
                connectedToHex = 0;
            }

        } catch (Exception e) {
            Log.d(TAG, "no link found. "+e.getMessage());
        }
    }

    /**
     * Registers a local service and then initiates a service discovery
     */
    private void startRegistrationAndDiscovery() {
        Map<String, String> record = new HashMap<String, String>();
        record.put(TXTRECORD_PROP_AVAILABLE, "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
        manager.addLocalService(channel, service, new ActionListener() {

            @Override
            public void onSuccess() {
                appendStatus("Added Local Service");
            }

            @Override
            public void onFailure(int error) {
                appendStatus("Failed to add a service");
            }
        });

        discoverService();

    }

    private void discoverService() {

        /*
         * Register listeners for DNS-SD services. These are callbacks invoked
         * by the system when a service is actually discovered.
         */

        manager.setDnsSdResponseListeners(channel,
                new DnsSdServiceResponseListener() {

                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                            String registrationType, WifiP2pDevice srcDevice) {

                        // A service has been discovered. Is this our app?

                        if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {

                            // update the UI and add the item the discovered
                            // device.
                            WiFiDirectServicesList fragment = (WiFiDirectServicesList) getFragmentManager()
                                    .findFragmentByTag("services");
                            if (fragment != null) {
                                WiFiDevicesAdapter adapter = ((WiFiDevicesAdapter) fragment.getListAdapter());
                                WiFiP2pService service = new WiFiP2pService();
                                service.device = srcDevice;
                                service.instanceName = instanceName;
                                service.serviceRegistrationType = registrationType;
                                adapter.add(service);
                                adapter.notifyDataSetChanged();
                                addNeighborHex(service);
                                Log.d(TAG, "onBonjourServiceAvailable "
                                        + instanceName);
                            }
                        }

                    }
                }, new DnsSdTxtRecordListener() {

                    /**
                     * A new TXT record is available. Pick up the advertised
                     * buddy name.
                     */
                    @Override
                    public void onDnsSdTxtRecordAvailable(
                            String fullDomainName, Map<String, String> record,
                            WifiP2pDevice device) {
                        Log.d(TAG,
                                device.deviceName + " is "
                                        + record.get(TXTRECORD_PROP_AVAILABLE));
                    }
                });

        // After attaching listeners, create a service request and initiate
        // discovery.
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        manager.addServiceRequest(channel, serviceRequest,
                new ActionListener() {

                    @Override
                    public void onSuccess() {
                        appendStatus("Added service discovery request");
                    }

                    @Override
                    public void onFailure(int arg0) {
                        appendStatus("Failed adding service discovery request");
                    }
                });
        manager.discoverServices(channel, new ActionListener() {

            @Override
            public void onSuccess() {
                appendStatus("Service discovery initiated");
            }

            @Override
            public void onFailure(int arg0) {
                appendStatus("Service discovery failed");

            }
        });
    }

    @Override
    public void connectP2p(WiFiP2pService service) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = service.device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        if (serviceRequest != null)
            manager.removeServiceRequest(channel, serviceRequest,
                    new ActionListener() {

                        @Override
                        public void onSuccess() {
                        }

                        @Override
                        public void onFailure(int arg0) {
                        }
                    });

        manager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                appendStatus("Connecting to service");
            }

            @Override
            public void onFailure(int errorCode) {
                appendStatus("Failed connecting to service");
            }
        });
    }

    private final static String DELIMETER = "```";
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                Log.d(TAG, readMessage);
                (chatFragment).pushMessage("Buddy: " + readMessage);
                String [] splitStr = readMessage.split(DELIMETER);
                if (splitStr.length > 3) {
                    String from = splitStr[0];
                    String urlreq = splitStr[1];
                    String meta = splitStr[2];
                    String data = splitStr[3];
                    Log.d(TAG, "just got HTML data from "+from+": "+data);

                    if (meta.equals("GET") && !urlreq.equals("_")) {
                        Log.d(TAG, "about to download website");
                        new DownloadWebsiteTask(from, urlreq, meta, data).execute(urlreq);
                    } else if (!data.equals(" ")) {
                        Log.d(TAG, "about to display webview");
                        WebView webView = (WebView) findViewById(R.id.website);
                        webView.loadData(data, "text/html", null);
                        webView.setVisibility(View.VISIBLE);

                    }
                }

                break;

            case MY_HANDLE:
                Object obj = msg.obj;
                chatFragment.setChatManager((ChatManager) obj);
                StringBuilder message = new StringBuilder();
                message.append(username);
                message.append(DELIMETER);
                message.append(searchQuery);
                message.append(DELIMETER);
                message.append("GET"); // meta
                message.append(DELIMETER);
                message.append(" "); // data

                chatFragment.write(message.toString().getBytes());
        }
        return true;
    }

    public class DownloadWebsiteTask extends AsyncTask<String, Void, String> {

        String mFrom;
        String mUrlreq;
        String mMeta;
        String mData;

        public DownloadWebsiteTask(String from, String urlreq, String meta, String data) {
            mFrom = from;
            mUrlreq = urlreq;
            mMeta = meta;
            mData = data;
        }

        @Override
        protected String doInBackground(String... uri) {
            HttpClient httpclient = new DefaultHttpClient();
            HttpResponse response;
            String responseString = null;
            try {
                response = httpclient.execute(new HttpGet(uri[0]));
                StatusLine statusLine = response.getStatusLine();
                if(statusLine.getStatusCode() == HttpStatus.SC_OK) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    response.getEntity().writeTo(out);
                    out.close();
                    responseString = out.toString();
                } else {
                    //Closes the connection.
                    response.getEntity().getContent().close();
                    throw new IOException(statusLine.getReasonPhrase());
                }
            } catch (ClientProtocolException e) {
                //TODO Handle problems..
            } catch (IOException e) {
                //TODO Handle problems..
            }
            return responseString;
        }

        @Override
        public void onPostExecute(String result) {
            if (result != null) {

                Log.d(TAG, result);

                StringBuilder message = new StringBuilder();
                message.append(username);
                message.append(DELIMETER);
                message.append(mUrlreq);
                message.append(DELIMETER);
                message.append("RESP"); // meta
                message.append(DELIMETER);
                message.append(result); // data

                chatFragment.write(message.toString().getBytes());
            } else {
                Log.d(TAG, "post execute err. result is null");
            }
        }
    }



    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo) {
        Thread handler = null;
        /*
         * The group owner accepts connections using a server socket and then spawns a
         * client socket for every client. This is handled by {@code
         * GroupOwnerSocketHandler}
         */

        if (p2pInfo.isGroupOwner) {
            Log.d(TAG, "Connected as group owner");
            try {
                handler = new GroupOwnerSocketHandler(
                        ((MessageTarget) this).getHandler());
                handler.start();
            } catch (IOException e) {
                Log.d(TAG,
                        "Failed to create a server thread - " + e.getMessage());
                return;
            }
        } else {
            Log.d(TAG, "Connected as peer");
            handler = new ClientSocketHandler(
                    ((MessageTarget) this).getHandler(),
                    p2pInfo.groupOwnerAddress);
            handler.start();
        }


        chatFragment = new WiFiChatFragment();
        getFragmentManager().beginTransaction().replace(R.id.container_root, chatFragment).commit();



        statusTxtView.setVisibility(View.GONE);
    }

    public void appendStatus(String status) {
        String current = statusTxtView.getText().toString();
        statusTxtView.setText(current + "\n" + status);
    }

    public void hex1Clicked(View v) {
        Toast.makeText(getApplicationContext(), serviceArrayList.get(0).device.deviceName, Toast.LENGTH_SHORT).show();
    }

    public void hex2Clicked(View v) {
        Toast.makeText(getApplicationContext(), serviceArrayList.get(1).device.deviceName, Toast.LENGTH_SHORT).show();

    }

    public void hex3Clicked(View v) {
        Toast.makeText(getApplicationContext(), serviceArrayList.get(2).device.deviceName, Toast.LENGTH_SHORT).show();

    }

    public void hex4Clicked(View v) {
        Toast.makeText(getApplicationContext(), serviceArrayList.get(3).device.deviceName, Toast.LENGTH_SHORT).show();

    }
}
