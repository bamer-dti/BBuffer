package pt.bamer.bamerosbuffer.gcm;

import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.couchbase.lite.replicator.Replication;

import java.net.MalformedURLException;
import java.net.URL;

import pt.bamer.bamerosbuffer.couchbase.ServicoCouchBase;

public class GcmMessageHandler extends IntentService {

    public static final String CODIGO_MENSAGEM = "GcmMessageHandler";
    private static final String TAG = GcmMessageHandler.class.getSimpleName();
    private Handler handler;

    public GcmMessageHandler() {
        super(CODIGO_MENSAGEM);
    }

    private Intent mIntent;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();
        Log.d(TAG, "INICIOU O SERVIÇO DE BORADCASTING");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mIntent = intent;
        showToast();
        try {
            URL url = new URL(ServicoCouchBase.COUCH_SERVER_AND_DB_URL);
            Replication pull = ServicoCouchBase.getInstancia().getDatabase().createPullReplication(url);
            pull.addChangeListener(getReplicationListener());
            pull.start();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

    }

    private Replication.ChangeListener getReplicationListener() {
        return new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.i("GCM", "replication status is : " + event.getSource().getStatus());
                if (event.getSource().getStatus() == Replication.ReplicationStatus.REPLICATION_STOPPED) {
                    GcmBroadcastReceiver.completeWakefulIntent(mIntent);
                }
            }
        };
    }

    public void showToast() {
        handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), "Server ping - sync down!", Toast.LENGTH_LONG).show();
            }
        });

    }
}