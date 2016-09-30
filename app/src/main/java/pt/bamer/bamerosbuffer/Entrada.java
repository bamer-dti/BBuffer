package pt.bamer.bamerosbuffer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Query;
import com.couchbase.lite.replicator.Replication;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;
import pt.bamer.bamerosbuffer.couchbase.ServicoCouchBase;
import pt.bamer.bamerosbuffer.utils.Constantes;
import pt.bamer.bamerosbuffer.utils.ValoresDefeito;

public class Entrada extends AppCompatActivity {

    private static final String TAG = Entrada.class.getSimpleName();
    private SmoothProgressBar pb_smooth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entrada);

        Spinner spinn_seccao = (Spinner) findViewById(R.id.spinn_seccao);
        final SharedPreferences prefs = MrApp.getPrefs();
        final String seccao = prefs.getString(Constantes.PREF_SECCAO, ValoresDefeito.SECCAO);

        Log.i(TAG, "SECÇÃO PREFERENCE: " + seccao);
        String[] array_seccoes = getResources().getStringArray(R.array.array_seccao);
        int pos = Arrays.asList(array_seccoes).indexOf(seccao);
        spinn_seccao.setSelection(pos);
        spinn_seccao.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, final View view, int i, long l) {
                String novaSeccao = adapterView.getItemAtPosition(i).toString();
                if (!novaSeccao.equals(seccao)) {

                    int versao = prefs.getInt(Constantes.PREF_VERSAO_VIEWS_COUCHBASE, 1);
                    versao++;

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(Constantes.PREF_SECCAO, novaSeccao);
                    editor.putInt(Constantes.PREF_VERSAO_VIEWS_COUCHBASE, versao);
                    editor.commit();

                    Toast.makeText(view.getContext(), "Reiniciar a APP para refrescar Bases de Dados", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Spinner spinn_estado = (Spinner) findViewById(R.id.spinner_estado);
        final String estado = prefs.getString(Constantes.PREF_ESTADO, ValoresDefeito.ESTADO);
        Log.i(TAG, "ESTADO PREFERENCE: " + estado);
        String[] array_estados = getResources().getStringArray(R.array.array_estados);
        pos = Arrays.asList(array_estados).indexOf(estado);
        spinn_estado.setSelection(pos);
        spinn_estado.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, final View view, int i, long l) {
                String novoEstado = adapterView.getItemAtPosition(i).toString();
                if (!novoEstado.equals(estado)) {

                    int versao = prefs.getInt(Constantes.PREF_VERSAO_VIEWS_COUCHBASE, 1);
                    versao++;

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(Constantes.PREF_ESTADO, novoEstado);
                    editor.putInt(Constantes.PREF_VERSAO_VIEWS_COUCHBASE, versao);
                    editor.commit();

                    Toast.makeText(view.getContext(), "Reiniciar a APP para refrescar Bases de Dados", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        final Spinner spinner_funcionario = (Spinner) findViewById(R.id.spinner_funcionario);

        Button butok = (Button) findViewById(R.id.butok);
        butok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MrApp.setOperador(spinner_funcionario.getSelectedItem().toString());
                Intent intent = new Intent(view.getContext(), PainelGlobal.class);
                startActivity(intent);
            }
        });

        Button buteliminardb = (Button) findViewById(R.id.butdeletedatabase);
        buteliminardb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    ServicoCouchBase.getInstancia().getDatabase().delete();
                } catch (CouchbaseLiteException e) {
                    e.printStackTrace();
                }
                Toast.makeText(view.getContext(), "Reiniciar a APP para refrescar Bases de Dados", Toast.LENGTH_LONG).show();
                finish();
            }
        });

        pb_smooth = (SmoothProgressBar) findViewById(R.id.pb_smooth);
        pb_smooth.setVisibility(View.INVISIBLE);

        configObservable();

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Query query = ServicoCouchBase.getInstancia().viewLinhasOSBIQttAGrupada.createQuery();
                try {
                    query.run();
                } catch (CouchbaseLiteException e) {
                    e.printStackTrace();
                }
            }
        });
        t.run();


    }

    private void configObservable() {
        Observer observador = new Observer() {
            @Override
            public void update(Observable observable, final Object data) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        MrApp.SyncProgress progress = (MrApp.SyncProgress) data;
                        com.couchbase.lite.util.Log.v(TAG, "COUCHBASE. Efectuado: %d Total: %d Estado: %s", progress.completedCount, progress.totalCount, progress.status);
                        if (progress.status == Replication.ReplicationStatus.REPLICATION_ACTIVE) {
                            pb_smooth.setVisibility(View.VISIBLE);
                        } else {
                            pb_smooth.setVisibility(View.INVISIBLE);
                        }
                    }

                });
            }
        };
        MrApp.getOnSyncProgressChangeObservable().addObserver(observador);

        final long intervalo = 5000;
        TimerTask esconderProgresso = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pb_smooth.getVisibility() == View.VISIBLE) {
                            Log.i(TAG, "pb_smooth está visivel à " + (intervalo / 1000) + " segundos");
                            pb_smooth.setVisibility(View.INVISIBLE);
                        }
                    }
                });
            }
        };

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(esconderProgresso, intervalo, intervalo);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ServicoCouchBase.getInstancia().stop();
        System.exit(0);
    }
}
