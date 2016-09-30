package pt.bamer.bamerosbuffer;

import android.app.Activity;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;

import com.couchbase.lite.replicator.Replication;

import java.util.Observable;

import pt.bamer.bamerosbuffer.couchbase.ServicoCouchBase;
import pt.bamer.bamerosbuffer.utils.Constantes;
import pt.bamer.bamerosbuffer.utils.ValoresDefeito;

public class MrApp extends Application {
    private static SharedPreferences prefs;
    private static String operador;
    private static String seccao;
    private static String estado;
    private static ToneGenerator toneG;
    private static ProgressDialog dialogoInterminavel;
    private static OnSyncProgressChangeObservable onSyncProgressChangeObservable;
    private ServicoCouchBase servicoCouchBase;

    public static SharedPreferences getPrefs() {
        return prefs;
    }

    public static void setOperador(String operador) {
        MrApp.operador = operador;
    }

    public static String getEstado() {
        return estado;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(Constantes.PREFS_NAME, MODE_PRIVATE);
        seccao = prefs.getString(Constantes.PREF_SECCAO, ValoresDefeito.SECCAO);
        estado = prefs.getString(Constantes.PREF_ESTADO, ValoresDefeito.ESTADO);
        toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        servicoCouchBase = ServicoCouchBase.getInstancia();
        servicoCouchBase.start(this);
        onSyncProgressChangeObservable = new OnSyncProgressChangeObservable();

    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        servicoCouchBase.stop();
    }

    public static String getSeccao() {
        return seccao;
    }

    public static ToneGenerator getToneG() {
        return toneG;
    }

    public static void mostrarAlertToWait(final Activity activity, final String mensagem) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (dialogoInterminavel == null) {
                    dialogoInterminavel = new ProgressDialog(activity);
                    dialogoInterminavel.setMessage(mensagem);
                    dialogoInterminavel.show();
                } else {
                    dialogoInterminavel.setMessage(mensagem);
                }
            }
        });
    }

    public static void esconderAlertToWait(Activity activity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (dialogoInterminavel != null) {
                    dialogoInterminavel.hide();
                    dialogoInterminavel.dismiss();
                    dialogoInterminavel = null;
                }
            }
        });
    }

    public synchronized void updateSyncProgress(int completedCount, int totalCount, Replication.ReplicationStatus status) {
        onSyncProgressChangeObservable.notifyChanges(completedCount, totalCount, status);
    }

    public class OnSyncProgressChangeObservable extends Observable {
        private void notifyChanges(int completedCount, int totalCount, Replication.ReplicationStatus status) {
            SyncProgress progress = new SyncProgress();
            progress.completedCount = completedCount;
            progress.totalCount = totalCount;
            progress.status = status;
            setChanged();
            notifyObservers(progress);
        }
    }

    public class SyncProgress {
        public int completedCount;
        public int totalCount;
        public Replication.ReplicationStatus status;
    }

    public static OnSyncProgressChangeObservable getOnSyncProgressChangeObservable() {
        return onSyncProgressChangeObservable;
    }
}
