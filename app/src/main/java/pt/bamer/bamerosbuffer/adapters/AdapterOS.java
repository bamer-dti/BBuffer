package pt.bamer.bamerosbuffer.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiveQuery;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import pt.bamer.bamerosbuffer.Dossier;
import pt.bamer.bamerosbuffer.PainelGlobal;
import pt.bamer.bamerosbuffer.R;
import pt.bamer.bamerosbuffer.couchbase.CamposCouch;
import pt.bamer.bamerosbuffer.couchbase.ServicoCouchBase;
import pt.bamer.bamerosbuffer.utils.Constantes;
import pt.bamer.bamerosbuffer.utils.Funcoes;
import pt.bamer.bamerosbuffer.utils.ValoresDefeito;


public class AdapterOS extends RecyclerView.Adapter {
    private static final String TAG = AdapterOS.class.getSimpleName();

    private final Context context;
    private QueryEnumerator enumerator;

    public AdapterOS(final Context context) {
        this.context = context;

        Query queryViewOS_CAB = ServicoCouchBase.getInstancia().viewOS_CABOrderCorteOrdemBostampCor.createQuery();
        queryViewOS_CAB.setLimit(ValoresDefeito.LIMITE_DE_OS);
        LiveQuery livequeryOS_CAB = queryViewOS_CAB.toLiveQuery();
        livequeryOS_CAB.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(final LiveQuery.ChangeEvent event) {
                updateSourceData(event.getRows());
            }
        });
        livequeryOS_CAB.start();

        LiveQuery liveOSPROD = ServicoCouchBase.getInstancia().viewOS_CABOrderCorteOrdemBostampCor.createQuery().toLiveQuery();
        liveOSPROD.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                try {
                    Query query = ServicoCouchBase.getInstancia().viewOS_CABOrderCorteOrdemBostampCor.createQuery();
                    query.setLimit(ValoresDefeito.LIMITE_DE_OS);
                    QueryEnumerator queryEnumerator = query.run();
                    updateSourceData(queryEnumerator);
                } catch (CouchbaseLiteException e) {
                    e.printStackTrace();
                }
            }
        });
        liveOSPROD.start();

        LiveQuery liveTimers = ServicoCouchBase.getInstancia().viewTempos.createQuery().toLiveQuery();
        liveTimers.addChangeListener(new LiveQuery.ChangeListener() {
            @Override
            public void changed(LiveQuery.ChangeEvent event) {
                try {
                    Query query = ServicoCouchBase.getInstancia().viewOS_CABOrderCorteOrdemBostampCor.createQuery();
                    query.setLimit(ValoresDefeito.LIMITE_DE_OS);
                    QueryEnumerator queryEnumerator = query.run();
                    updateSourceData(queryEnumerator);
                } catch (CouchbaseLiteException e) {
                    e.printStackTrace();
                }
            }
        });
        liveTimers.start();
    }

    private void updateSourceData(final QueryEnumerator queryEnumerator) {
        ((PainelGlobal) context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                enumerator = queryEnumerator;
                Log.v(TAG, "AdapterOS tem " + enumerator.getCount() + " linhas!");
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(context).inflate(R.layout.view_osbo, parent, false);
        return new AdapterOS.ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        AdapterOS.ViewHolder viewHolder = (AdapterOS.ViewHolder) holder;
        QueryRow queryRow = getItem(position);
        if (queryRow == null) {
            return;
        }
        DateTimeFormatter dtf = DateTimeFormat.forPattern("dd.MM.yyyy");

        Document document = queryRow.getDocument();
        viewHolder.tv_fref.setText(document.getProperty(CamposCouch.FIELD_FREF) + " - " + document.getProperty(CamposCouch.FIELD_NMFREF));
        viewHolder.tv_obrano.setText("OS " + document.getProperty(CamposCouch.FIELD_OBRANO));
        viewHolder.tv_descricao.setText(document.getProperty(CamposCouch.FIELD_OBS).toString());


        LocalDateTime localDateTime = Funcoes.cToT(document.getProperty(CamposCouch.FIELD_DTCORTEF).toString());
        viewHolder.tv_dtcortef.setText(dtf.print(localDateTime));

        localDateTime = Funcoes.cToT(document.getProperty(CamposCouch.FIELD_DTTRANSF).toString());
        viewHolder.tv_dttransf.setText(dtf.print(localDateTime));

        final String bostamp = document.getProperty(CamposCouch.FIELD_BOSTAMP).toString();

        new TaskCalculoQtt(bostamp, viewHolder.tv_qtt).execute();

        new TaskCalculoQttProduzida(bostamp, viewHolder.tv_qttfeita).execute();

        new TaskCalcularTempo(bostamp, viewHolder.tv_temporal).execute();

        new TaskPosicaoDoUltimoTempo(bostamp, viewHolder.llclick).execute();

        viewHolder.llclick.setTag(bostamp);

        viewHolder.llclick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), Dossier.class);
                intent.putExtra(Constantes.INTENT_EXTRA_BOSTAMP, view.getTag().toString());
                intent.putExtra(Constantes.INTENT_EXTRA_MODO_OPERACIONAL, Constantes.MODO_STARTED);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        });
    }

    private class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView tv_fref;
        private final TextView tv_obrano;
        private final TextView tv_descricao;
        private final TextView tv_dtcortef;
        private final TextView tv_dttransf;
        private final TextView tv_qtt;
        private final TextView tv_qttfeita;
        private final TextView tv_temporal;
        private final LinearLayout llclick;

        public ViewHolder(View view) {
            super(view);
            tv_fref = (TextView) view.findViewById(R.id.tv_fref);
            tv_obrano = (TextView) view.findViewById(R.id.tv_obrano);
            tv_descricao = (TextView) view.findViewById(R.id.tv_descricao);
            tv_dtcortef = (TextView) view.findViewById(R.id.tv_dtcortef);
            tv_dttransf = (TextView) view.findViewById(R.id.tv_dttransf);
            tv_qtt = (TextView) view.findViewById(R.id.tv_qtt);
            tv_qttfeita = (TextView) view.findViewById(R.id.tv_qttfeita);
            tv_temporal = (TextView) view.findViewById(R.id.tv_temporal);
            llclick = (LinearLayout) view.findViewById(R.id.llclick);
        }
    }

    public QueryRow getItem(int posicao) {
        return enumerator.getRow(posicao);
    }

    @Override
    public int getItemCount() {
        return enumerator == null ? 0 : enumerator.getCount();
    }

    private class TaskCalculoQtt extends AsyncTask<Void, Void, Void> {
        private final String bostamp;
        private final TextView tv_qtt;
        private int qtt;

        public TaskCalculoQtt(String bostamp, TextView tv_qtt) {
            this.bostamp = bostamp;
            this.tv_qtt = tv_qtt;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            qtt = ServicoCouchBase.getInstancia().getPecasPorOS(bostamp);
            return null;
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void onPostExecute(Void aVoid) {
            tv_qtt.setText("" + qtt);
        }
    }

    private class TaskCalculoQttProduzida extends AsyncTask<Void, Void, Void> {
        private final String bostamp;
        private final TextView tv_qttfeita;
        private int qtt;

        public TaskCalculoQttProduzida(String bostamp, TextView tv_qttfeita) {
            this.bostamp = bostamp;
            this.tv_qttfeita = tv_qttfeita;
            this.qtt = 0;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            qtt = ServicoCouchBase.getInstancia().getPecasFeitasPorOS(bostamp);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            tv_qttfeita.setText(qtt != 0 ? "" + qtt : "");
        }
    }

    private class TaskCalcularTempo extends AsyncTask<Void, Void, Void> {
        private final String bostamp;
        private final TextView tv_temporal;
        private long tempoCalculado;

        public TaskCalcularTempo(String bostamp, TextView tv_temporal) {
            this.bostamp = bostamp;
            this.tv_temporal = tv_temporal;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            tempoCalculado = ServicoCouchBase.getInstancia().getTempoTotal(bostamp);
            return null;
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void onPostExecute(Void aVoid) {
            String textoTempo = Funcoes.milisegundos_em_HH_MM_SS(tempoCalculado * 1000);
            tv_temporal.setText("" + (tempoCalculado == 0 ? "" : textoTempo));
        }
    }

    private class TaskPosicaoDoUltimoTempo extends AsyncTask<Void, Void, Void> {
        private final String bostamp_;
        private final LinearLayout llclick_;
        private Document document;

        public TaskPosicaoDoUltimoTempo(String bostamp, LinearLayout llclick) {
            this.bostamp_ = bostamp;
            this.llclick_ = llclick;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            document = ServicoCouchBase.getInstancia().getUltimoTempo(bostamp_);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            PainelGlobal painel = (PainelGlobal) context;
            painel.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    llclick_.setBackgroundColor(Color.WHITE);
                }
            });

            if (document == null) {
                return;
            }
            Log.v(TAG, document.getProperties().toString());
            final int tipoTempo = Integer.parseInt(document.getProperty(CamposCouch.FIELD_POSICAO).toString());
            painel.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (tipoTempo == Constantes.MODO_STARTED) {
                        llclick_.setBackgroundColor(ContextCompat.getColor(llclick_.getContext(), R.color.md_green_100));
                    }
                }
            });
        }
    }
}
