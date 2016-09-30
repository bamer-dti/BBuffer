package pt.bamer.bamerosbuffer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiveQuery;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.replicator.Replication;
import com.hanks.htextview.HTextView;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;
import pt.bamer.bamerosbuffer.adapters.AdapterOS;
import pt.bamer.bamerosbuffer.charts.PieHoje;
import pt.bamer.bamerosbuffer.couchbase.CamposCouch;
import pt.bamer.bamerosbuffer.couchbase.ServicoCouchBase;
import pt.bamer.bamerosbuffer.gcm.GcmBroadcastReceiver;
import pt.bamer.bamerosbuffer.gcm.GcmMessageHandler;
import pt.bamer.bamerosbuffer.helpers.TimerDePainelGlobal;
import pt.bamer.bamerosbuffer.utils.Constantes;
import pt.bamer.bamerosbuffer.utils.Funcoes;

public class PainelGlobal extends AppCompatActivity {
    private static final String TAG = "LOG" + PainelGlobal.class.getSimpleName();
    private PainelGlobal activity = this;
    private SmoothProgressBar pb_smooth;
    private LiveQuery liveQueryInspeccao;
    private GcmBroadcastReceiver reMyreceive;
    private IntentFilter filter;
    private RecyclerView recycler_os;
    private LinearLayout ll_maquinas;
    private LiveQuery liveQueryMaquinas;
    private List<TimerDePainelGlobal> listaDeTimers;
    private PieHoje pieQtdsHoje;
    private Query queryQtdsParaAtrasos;
    private Query queryQtdsParaPie;
    private Query queryQtdsParaAmanha;
    private Query queryQtdsParaFuturo;
    private HTextView htv_qtt_antes;
    private HTextView htv_qtt_amanha;
    private HTextView htv_inspeccao_numero;
    private HTextView htv_qtt_futuro;
    private int qttParaInspecaoEmAberto;
    private LiveQuery liveQueryOSPROD;
    private LiveQuery liveQueryFuturo;
    private LiveQuery liveQueryAmanha;
    private LiveQuery liveQueryAtrasos;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        setContentView(R.layout.activity_painel_global);

        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        pb_smooth = (SmoothProgressBar) findViewById(R.id.pb_smooth);
        pb_smooth.setVisibility(View.INVISIBLE);

        CardView cardview_inspeccao = (CardView) findViewById(R.id.card_view_inspeccao);
        cardview_inspeccao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(view.getContext(), "a implementar", Toast.LENGTH_SHORT).show();
            }
        });

        htv_inspeccao_numero = (HTextView) findViewById(R.id.htv_prontas);

        ll_maquinas = (LinearLayout) findViewById(R.id.ll_maquinas);

        recycler_os = (RecyclerView) findViewById(R.id.recycler_os);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recycler_os.setLayoutManager(linearLayoutManager);

        painelAtrasados();

        painelPieHoje();

        painelAmanha();

        painelFuturo();

        createReceiver();

//        recycler_os.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
//            @Override
//            public boolean onPreDraw() {
//                recycler_os.getViewTreeObserver().removeOnPreDrawListener(this);
//
//                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) recycler_os.getLayoutParams();
//                params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
//                recycler_os.setLayoutParams(params);
//                return true;
//            }
//        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");
        configObservables();
        configLiveQuerys();
        AdapterOS adapterOS = new AdapterOS(this);
        recycler_os.setAdapter(adapterOS);
        pieQtdsHoje.setData(pieQtdsHoje.getQttProduzida(), pieQtdsHoje.getQttPedida());
        List<String> channels = ServicoCouchBase.getInstancia().getPullReplication().getChannels();
        for (String canal : channels) {
            Log.i(TAG, "***** CANAL " + canal);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        registerReceiver(reMyreceive, filter);
        estadoLiveQuerys(Constantes.MODO_STARTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        unregisterReceiver(reMyreceive);
        estadoLiveQuerys(Constantes.MODO_STOPED);
        pararCronometrosMyTimerActivity();
    }

    private void painelFuturo() {
        DateTime dateTime = new DateTime().withTimeAtStartOfDay();
        dateTime = dateTime.plusDays(2);
        LocalDateTime futuro = dateTime.toLocalDateTime();
        final String dataFuturoTxt = Funcoes.localDateTimeToStrFullaZeroHour(futuro);

        htv_qtt_futuro = (HTextView) findViewById(R.id.htv_qtt_futuro);

        queryQtdsParaFuturo = ServicoCouchBase.getInstancia().viewOS_CABODtcorteFBostamp.createQuery();
        queryQtdsParaFuturo.setStartKey(Arrays.asList(dataFuturoTxt, ""));
        queryQtdsParaFuturo.setEndKey(Arrays.asList("Z", "Z"));
        queryQtdsParaFuturo.runAsync(new Query.QueryCompleteListener() {
            @Override
            public void completed(QueryEnumerator rows, Throwable error) {
                Log.d(TAG, "dataFuturoTxt: " + dataFuturoTxt + " tem " + rows.getCount());
            }
        });
        liveQueryFuturo = queryQtdsParaFuturo.toLiveQuery();
        liveQueryFuturo.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                new TaskAlimentarFuturo(event.getRows()).execute();
            }
        });
    }

    private void painelAmanha() {
        DateTime dateTime = new DateTime().withTimeAtStartOfDay();
        dateTime = dateTime.plusDays(1);
        LocalDateTime amanha = dateTime.toLocalDateTime();
        final String dataAmanhaTxt = Funcoes.localDateTimeToStrFullaZeroHour(amanha);

        htv_qtt_amanha = (HTextView) findViewById(R.id.htv_qtt_amanha);

        queryQtdsParaAmanha = ServicoCouchBase.getInstancia().viewOS_CABODtcorteFBostamp.createQuery();
        queryQtdsParaAmanha.setStartKey(Arrays.asList(dataAmanhaTxt, ""));
        queryQtdsParaAmanha.setEndKey(Arrays.asList(dataAmanhaTxt, "Z"));
        queryQtdsParaAmanha.runAsync(new Query.QueryCompleteListener() {
            @Override
            public void completed(QueryEnumerator rows, Throwable error) {
                Log.d(TAG, "dataAmanhaTxt: " + dataAmanhaTxt + " tem " + rows.getCount());
            }
        });
        liveQueryAmanha = queryQtdsParaAmanha.toLiveQuery();
        liveQueryAmanha.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                new TaskAlimentarAmanha(event.getRows()).execute();
            }
        });
    }

    private void painelAtrasados() {
        DateTime yesterday = new DateTime().withTimeAtStartOfDay();
        yesterday = yesterday.minusDays(1);
        LocalDateTime hoje = yesterday.toLocalDateTime();
        String dataOntemTxt = Funcoes.localDateTimeToStrFullaZeroHour(hoje);

        htv_qtt_antes = (HTextView) findViewById(R.id.htv_qtt_antes);
        queryQtdsParaAtrasos = ServicoCouchBase.getInstancia().viewOS_CABODtcorteFBostamp.createQuery();
        queryQtdsParaAtrasos.setStartKey(Arrays.asList("", "Z"));
        queryQtdsParaAtrasos.setEndKey(Arrays.asList(dataOntemTxt, "Z"));
        liveQueryAtrasos = queryQtdsParaAtrasos.toLiveQuery();
        liveQueryAtrasos.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                new TaskAlimentarAtrasos(event.getRows()).execute();
            }
        });
    }

    private void painelPieHoje() {
        pieQtdsHoje = (PieHoje) findViewById(R.id.pie_hoje);

        //LiveQuery!
        queryQtdsParaPie = ServicoCouchBase.getInstancia().viewOS_CABODtcorteFBostamp.createQuery();
        DateTime today = new DateTime().withTimeAtStartOfDay();
        LocalDateTime hoje = today.toLocalDateTime();
        String dataHojeTxt = Funcoes.localDateTimeToStrFullaZeroHour(hoje);
        Log.d(TAG, "dataHojeTxt = " + dataHojeTxt);
        queryQtdsParaPie.setStartKey(Arrays.asList(dataHojeTxt, ""));
        queryQtdsParaPie.setEndKey(Arrays.asList(dataHojeTxt, "Z"));
        LiveQuery live = queryQtdsParaPie.toLiveQuery();
        live.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                new TaskAlimentarPie(event.getRows()).execute();
            }
        });
        live.start();
    }

    private void createReceiver() {
        reMyreceive = new GcmBroadcastReceiver();
        filter = new IntentFilter(GcmMessageHandler.CODIGO_MENSAGEM);
        Intent i = new Intent(this, GcmMessageHandler.class);
        startService(i);
    }

    private void pararCronometrosMyTimerActivity() {
        if (listaDeTimers == null) {
            return;
        }
        int i = 0;
        for (TimerDePainelGlobal timerDePainelGlobal : listaDeTimers) {
            i++;
            timerDePainelGlobal.cancel();
            timerDePainelGlobal.purge();
        }
        Log.i(TAG, "**Cancelados " + i + " cronometros TimerDePainelGlobal**");
    }

    private void estadoLiveQuerys(int modo) {
        switch (modo) {
            case Constantes.MODO_STARTED:
                liveQueryInspeccao.start();
                liveQueryOSPROD.start();
                liveQueryMaquinas.start();
                liveQueryFuturo.start();
                liveQueryAmanha.start();
                liveQueryAtrasos.start();
                Log.d(TAG, "Serviços livequery iniciados");
                return;

            case Constantes.MODO_STOPED:
                liveQueryInspeccao.stop();
                liveQueryOSPROD.stop();
                liveQueryMaquinas.stop();
                liveQueryFuturo.stop();
                liveQueryAmanha.stop();
                liveQueryAtrasos.stop();
                Log.d(TAG, "Serviços livequery parados");
                return;

            default:
        }
    }

    private void configObservables() {
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

    private void configLiveQuerys() {
        liveQueryInspeccao = ServicoCouchBase.getInstancia().viewOS_CABOrderCorteOrdemBostampCor.createQuery().toLiveQuery();
        liveQueryInspeccao.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                QueryEnumerator queryEnumerator = event.getRows();
                alimentarTextoInspeccao(queryEnumerator);
            }
        });

        Query query = ServicoCouchBase.getInstancia().viewDistinctMaquinas.createQuery();
        query.setGroupLevel(1);
        liveQueryMaquinas = query.toLiveQuery();
        liveQueryMaquinas.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                efectuarMostradorMaquinas(event.getRows());
            }
        });

        liveQueryOSPROD = ServicoCouchBase.getInstancia().viewOSPROD.createQuery().toLiveQuery();
        liveQueryOSPROD.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                try {
                    Log.i(TAG, "liveProducaoPaineis: " + event.getRows().getCount());
                    QueryEnumerator queryEnumerator = queryQtdsParaPie.run();
                    new TaskAlimentarPie(queryEnumerator).execute();

                    queryEnumerator = queryQtdsParaAtrasos.run();
                    new TaskAlimentarAtrasos(queryEnumerator).execute();

                    queryEnumerator = queryQtdsParaAmanha.run();
                    new TaskAlimentarAmanha(queryEnumerator).execute();

                    queryEnumerator = queryQtdsParaFuturo.run();
                    new TaskAlimentarFuturo(queryEnumerator).execute();

                } catch (CouchbaseLiteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void efectuarMostradorMaquinas(QueryEnumerator queryEnumerator) {
        Log.i(TAG, "Existem " + queryEnumerator.getCount() + " machinas na secção " + MrApp.getSeccao());

        List<String> listaMaquinas = new ArrayList<>();
        for (int i = 0; i < queryEnumerator.getCount(); i++) {
            final String maquina = queryEnumerator.getRow(i).getKey().toString();
            Log.i(TAG, "Nome da máquina: " + maquina);
            listaMaquinas.add(maquina);
            for (int z = 0; z < ll_maquinas.getChildCount(); z++) {
                View obj = ll_maquinas.getChildAt(z);
                if (obj instanceof Cartao_Maquina) {
                    Cartao_Maquina cartao = (Cartao_Maquina) obj;
                    if (cartao.getIdInterno().equals(maquina)) {
                        //Existe a máquina!
                        continue;
                    }
                    //Não existe
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            int margem = (int) getResources().getDimension(R.dimen.margem_cartao_maquina);
                            params.setMargins(margem, margem, margem, margem);
                            params.gravity = Gravity.CENTER_HORIZONTAL;
                            ll_maquinas.addView(new Cartao_Maquina(activity, maquina)
                                    , params
                            );
                        }
                    });
                }
            }
        }

        //Retirar máquinas obsoletas
        ArrayList<View> listaMaquinasObsoletas = new ArrayList<>();
        int numChilds = ll_maquinas.getChildCount();
        for (int i = 0; i < numChilds; i++) {
            boolean interessa = false;
            View child = ll_maquinas.getChildAt(i);
            if (child instanceof Cartao_Maquina) {
                Cartao_Maquina cartao = (Cartao_Maquina) child;
                String idCartao = cartao.getIdInterno();
                for (int z = 0; z < listaMaquinas.size(); z++) {
                    String maquina = listaMaquinas.get(z);
                    if (maquina.equals(idCartao)) {
                        interessa = true;
                    }
                }
                if (!interessa) {
                    listaMaquinasObsoletas.add(child);
                }
            }
        }

        for (final View view : listaMaquinasObsoletas) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ll_maquinas.removeView(view);
                    ll_maquinas.invalidate();
                }
            });
        }

        //NÃO EXISTEM REGISTOS DE MÁQUINAS!??!!

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int childs = ll_maquinas.getChildCount();
                if (childs == 0) {
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    int margem = (int) getResources().getDimension(R.dimen.margem_cartao_maquina);
                    params.setMargins(margem, margem, margem, margem);
                    params.gravity = Gravity.CENTER_HORIZONTAL;
                    ll_maquinas.addView(new Cartao_Maquina(activity, "NÃO EXISTEM\nREGISTOS DE MÁQUINAS!")
                            , params
                    );
                }
            }
        });
    }

    private void alimentarTextoInspeccao(QueryEnumerator queryEnumerator) {
        int qttTotal = 0;
        for (int i = 0; i < queryEnumerator.getCount(); i++) {
            QueryRow queryRow = queryEnumerator.getRow(i);
            //noinspection unchecked
            String bostamp = ((List<Object>) queryRow.getKey()).get(2).toString();
            int qttPedida = ServicoCouchBase.getInstancia().getPecasPorOS(bostamp);
            int qttProduzida = ServicoCouchBase.getInstancia().getPecasOSPROD(bostamp);
            if (qttPedida == qttProduzida) {
                qttTotal++;
            }
        }
        //BEEPER
        if (this.qttParaInspecaoEmAberto != qttTotal) {
            if (qttParaInspecaoEmAberto < qttTotal) {
//                new Funcoes.Beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200).execute();
                new Funcoes.Beep(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL, 200).execute();
            }
            this.qttParaInspecaoEmAberto = qttTotal;
        }

        final int finalQttTotal = qttTotal;
        runOnUiThread(new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                htv_inspeccao_numero.animateText("" + finalQttTotal);
            }
        });
    }

    public void addTimer(TimerDePainelGlobal timerDePainelGlobal) {
        if (listaDeTimers == null) {
            listaDeTimers = new ArrayList<>();
        }
        listaDeTimers.add(timerDePainelGlobal);
    }

    private class TaskAlimentarPie extends AsyncTask<Void, Void, Void> {
        private final QueryEnumerator queryEnumerator;
        private int qttPed;
        private int qttProd;

        public TaskAlimentarPie(QueryEnumerator queryEnumerator) {
            this.queryEnumerator = queryEnumerator;
            Log.i(TAG, "EATING the pie!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (int i = 0; i < queryEnumerator.getCount(); i++) {
                QueryRow row = queryEnumerator.getRow(i);
                String bostamp = row.getDocument().getProperty(CamposCouch.FIELD_BOSTAMP).toString();
                //Qtd
                qttPed += ServicoCouchBase.getInstancia().getPecasPorOS(bostamp);
                qttProd += ServicoCouchBase.getInstancia().getPecasFeitasPorOS(bostamp);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            pieQtdsHoje.setData(qttProd, qttPed);
        }
    }

    private class TaskAlimentarAtrasos extends AsyncTask<Void, Void, Void> {
        private final QueryEnumerator queryEnumerator;
        private int qttPed;
        private int qttProd;

        public TaskAlimentarAtrasos(QueryEnumerator queryEnumerator) {
            this.queryEnumerator = queryEnumerator;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (int i = 0; i < queryEnumerator.getCount(); i++) {
                QueryRow row = queryEnumerator.getRow(i);
                String bostamp = row.getDocument().getProperty(CamposCouch.FIELD_BOSTAMP).toString();
                //Qtd
                qttPed += ServicoCouchBase.getInstancia().getPecasPorOS(bostamp);
                qttProd += ServicoCouchBase.getInstancia().getPecasFeitasPorOS(bostamp);
            }
            return null;
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void onPostExecute(Void aVoid) {
            String textoActual = htv_qtt_antes.getText().toString();
            String textoNovo = "" + (qttPed - qttProd);
            if (!textoNovo.equals(textoActual))
                htv_qtt_antes.animateText(textoNovo);
        }
    }

    private class TaskAlimentarAmanha extends AsyncTask<Void, Void, Void> {
        private final QueryEnumerator queryEnumerator;
        private int qttPed;
        private int qttProd;

        public TaskAlimentarAmanha(QueryEnumerator queryEnumerator) {
            this.queryEnumerator = queryEnumerator;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (int i = 0; i < queryEnumerator.getCount(); i++) {
                QueryRow row = queryEnumerator.getRow(i);
                String bostamp = row.getDocument().getProperty(CamposCouch.FIELD_BOSTAMP).toString();
                //Qtd
                qttPed += ServicoCouchBase.getInstancia().getPecasPorOS(bostamp);
                qttProd += ServicoCouchBase.getInstancia().getPecasFeitasPorOS(bostamp);
            }
            return null;
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void onPostExecute(Void aVoid) {
            String textoActual = htv_qtt_amanha.getText().toString();
            String textoNovo = "" + (qttPed - qttProd);
            if (!textoNovo.equals(textoActual))
                htv_qtt_amanha.animateText(textoNovo);
        }
    }

    private class TaskAlimentarFuturo extends AsyncTask<Void, Void, Void> {
        private final QueryEnumerator queryEnumerator;
        private int qttPed;
        private int qttProd;

        public TaskAlimentarFuturo(QueryEnumerator queryEnumerator) {
            this.queryEnumerator = queryEnumerator;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (int i = 0; i < queryEnumerator.getCount(); i++) {
                QueryRow row = queryEnumerator.getRow(i);
                String bostamp = row.getDocument().getProperty(CamposCouch.FIELD_BOSTAMP).toString();
                //Qtd
                qttPed += ServicoCouchBase.getInstancia().getPecasPorOS(bostamp);
                qttProd += ServicoCouchBase.getInstancia().getPecasFeitasPorOS(bostamp);
            }
            return null;
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void onPostExecute(Void aVoid) {
            String textoActual = htv_qtt_futuro.getText().toString();
            String textoNovo = "" + (qttPed - qttProd);
            if (!textoNovo.equals(textoActual))
                htv_qtt_futuro.animateText(textoNovo);
        }
    }
}
