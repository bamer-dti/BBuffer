package pt.bamer.bamerosbuffer;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;

import com.couchbase.lite.Document;
import com.couchbase.lite.LiveQuery;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.View;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import pt.bamer.bamerosbuffer.adapters.TarefaRecyclerAdapter;
import pt.bamer.bamerosbuffer.couchbase.ServicoCouchBase;
import pt.bamer.bamerosbuffer.couchbase.CamposCouch;
import pt.bamer.bamerosbuffer.utils.Constantes;

public class Dossier extends AppCompatActivity {
    private static final String TAG = Dossier.class.getSimpleName();
    Dossier activity = this;
    private LiveQuery livequeryLinhasBI;
    private RecyclerView recyclerView;
    private LiveQuery livequeryQtdDossier;
    private String bostamp;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_dossier);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Paint paint = new Paint();
        paint.setStrokeWidth(1);
        paint.setColor(Color.DKGRAY);
        paint.setAntiAlias(true);
        paint.setPathEffect(new DashPathEffect(new float[]{25.0f, 25.0f}, 0));

        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(mLayoutManager);

        RecyclerView.ItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setAddDuration(1000);
        itemAnimator.setRemoveDuration(1000);
        recyclerView.setItemAnimator(itemAnimator);

        Bundle extras = getIntent().getExtras();
        bostamp = "";
        if (extras != null) {
            bostamp = extras.getString(Constantes.INTENT_EXTRA_BOSTAMP);
        }
        accionarLiveQuerys();

        configObservables();
    }

    private void configObservables() {
        MrApp.getOnSyncProgressChangeObservable().addObserver(new Observer() {
            @Override
            public void update(final Observable observable, final Object data) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        MrApp.SyncProgress progress = (MrApp.SyncProgress) data;
                        com.couchbase.lite.util.Log.v(TAG, "Sync progress changed.  Completed: %d Total: %d Status: %s", progress.completedCount, progress.totalCount, progress.status);
                        if (progress.status == Replication.ReplicationStatus.REPLICATION_ACTIVE) {
                            // TODO: 20-09-2016 Colocar a barra de progresso
                        } else {
                            // TODO: 20-09-2016 Esconder a barra de progresso
                        }
                    }
                });
            }
        });
    }

    public void accionarLiveQuerys() {
        View view = ServicoCouchBase.getInstancia().viewOSPROD;
        Query query1 = view.createQuery();
        livequeryQtdDossier = query1.toLiveQuery();
        livequeryQtdDossier.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                QueryEnumerator i = event.getRows();
                Log.i(TAG, "**** LIVEQUERY QTD PRODUZIDA NO DOSSIER RUNNING **** " + i.getCount() + ", bostamp " + bostamp);
                for (QueryRow queryRow : i) {
                    Document document = queryRow.getDocument();
                    final String bostamp = document.getProperty(CamposCouch.FIELD_BOSTAMP).toString();
                    final String dim = document.getProperty(CamposCouch.FIELD_DIM).toString();
                    final String mk = document.getProperty(CamposCouch.FIELD_MK).toString();
                    final String ref = document.getProperty(CamposCouch.FIELD_REF).toString();
                    final String design = document.getProperty(CamposCouch.FIELD_DESIGN).toString();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TarefaRecyclerAdapter tarefaRecyclerAdapter = (TarefaRecyclerAdapter) recyclerView.getAdapter();
                            if (tarefaRecyclerAdapter != null) {
                                tarefaRecyclerAdapter.actualizarQtdProd(bostamp, dim, mk, ref, design);
                            }
                            MrApp.esconderAlertToWait(activity);
                        }
                    });
                }
            }
        });

        Query query = ServicoCouchBase.getInstancia().viewLinhasOSBIQttAGrupada.createQuery();
        query.setStartKey(Arrays.asList(bostamp, "", "", "", ""));
        query.setEndKey(Arrays.asList(bostamp, "Z", "Z", "Z", "Z"));
        query.setGroupLevel(5);
        livequeryLinhasBI = query.toLiveQuery();
        livequeryLinhasBI.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                Log.i(TAG, "**** LIVEQUERY LINHAS DOSSIER MODO_STARTED ****");
                QueryEnumerator queryEnumerator = livequeryLinhasBI.getRows();
                new efectuarAdapter(queryEnumerator).execute();
            }
        });
    }

    private class efectuarAdapter extends AsyncTask<Void, Void, Void> {

        private final QueryEnumerator queryEnumerator;
        private boolean vertudo;
        private List<QueryRow> listaQueryRows;

        public efectuarAdapter(QueryEnumerator queryEnumerator) {
            this.queryEnumerator = queryEnumerator;
        }

        @Override
        protected void onPreExecute() {
            SharedPreferences prefs = MrApp.getPrefs();
            vertudo = prefs.getBoolean(Constantes.PREF_MOSTRAR_TODAS_LINHAS_PROD, true);
            MrApp.mostrarAlertToWait(activity, "A organizar dados...");
        }


        @Override
        protected Void doInBackground(Void... voids) {
            listaQueryRows = new ArrayList<>();
            while (queryEnumerator.hasNext()) {
                QueryRow queryRow = queryEnumerator.next();
                @SuppressWarnings("unchecked")
                List<String> keys = (List<String>) queryRow.getKey();
//            Log.i(TAG, keys.toString());
                String bostamp = keys.get(0);
                String dim = keys.get(1);
                String mk = keys.get(2);
                String ref = keys.get(3);
                String design = keys.get(4);
                int qtt = ServicoCouchBase.getInstancia().getQttAgrupadasOSBI(bostamp, dim, mk, ref, design);
                if (!vertudo) {
                    int produzida = 0;
                    if (qtt != produzida) {
                        listaQueryRows.add(queryRow);
                    }
                } else {
                    listaQueryRows.add(queryRow);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            final TarefaRecyclerAdapter tarefaRecyclerAdapter = new TarefaRecyclerAdapter(activity, listaQueryRows);
            runOnUiThread(new Runnable() {
                              @Override
                              public void run() {
                                  recyclerView.setAdapter(tarefaRecyclerAdapter);
                              }
                          }
            );
            MrApp.esconderAlertToWait(activity);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        livequeryLinhasBI.start();
        livequeryQtdDossier.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        livequeryLinhasBI.stop();
        livequeryQtdDossier.stop();
    }

    @Override
    protected void onDestroy() {
        livequeryLinhasBI.stop();
        livequeryLinhasBI = null;
        livequeryQtdDossier.stop();
        livequeryQtdDossier = null;
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

//    private void actionbarSetup() {
//        SharedPreferences prefs = MrApp.getPrefs();
//        boolean vis = prefs.getBoolean(Constantes.PREF_MOSTRAR_TODAS_LINHAS_PROD, true);
//        menu.findItem(R.id.itemmenu_mostrar_tudo).setTitle(vis ? Constantes.MOSTRAR_TUDO : Constantes.MOSTRAR_FILTRADO);
//        Query query = ServicoCouchBase.getInstancia().viewLinhasOSBIQttAGrupada.createQuery();
//        query.setStartKey(Arrays.asList(bostamp, "", "", "", ""));
//        query.setEndKey(Arrays.asList(bostamp, "Z", "Z", "Z", "Z"));
//        query.setGroupLevel(5);
//        try {
//            QueryEnumerator queryEnumerator = query.run();
//            new efectuarAdapter(queryEnumerator).execute();
//        } catch (CouchbaseLiteException e) {
//            e.printStackTrace();
//        }
//
//    }

}
