package pt.bamer.bamerosbuffer.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.couchbase.lite.QueryRow;
import com.couchbase.lite.util.Log;

import java.util.List;

import pt.bamer.bamerosbuffer.Dossier;
import pt.bamer.bamerosbuffer.MrApp;
import pt.bamer.bamerosbuffer.R;
import pt.bamer.bamerosbuffer.couchbase.ServicoCouchBase;
import pt.bamer.bamerosbuffer.utils.Constantes;

public class TarefaRecyclerAdapter extends RecyclerView.Adapter {
    @SuppressWarnings("unused")
    private static final String TAG = TarefaRecyclerAdapter.class.getSimpleName();
    private final Dossier activityDossier;
    private final List<QueryRow> lista;

    public TarefaRecyclerAdapter(Dossier activityDossier, List<QueryRow> lista) {
        this.activityDossier = activityDossier;
        this.lista = lista;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(this.activityDossier).inflate(R.layout.view_task, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        final TarefaRecyclerAdapter.ViewHolder viewHolder = (TarefaRecyclerAdapter.ViewHolder) holder;
        final QueryRow queryRow = getItem(position);
        if (queryRow == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) queryRow.getKey();
        final String bostamp = keys.get(0);
        final String dim = keys.get(1);
        final String mk = keys.get(2);
        final String ref = keys.get(3);
        final String design = keys.get(4);

        viewHolder.tv_ref.setText(ref + " - " + design);

        viewHolder.tv_qtt.setText("...");

        TaskQtd taskQtd = new TaskQtd(viewHolder);
        taskQtd.execute();

        viewHolder.tv_dim.setText(dim + (mk.equals("") ? "" : ", mk " + mk));
    }

    private QueryRow getItem(int position) {
        return lista != null ? lista.get(position) : null;
    }

    @Override
    public int getItemCount() {
        return lista != null ? lista.size() : 0;
    }

    public void actualizarQtdProd(String bostamp, String dim, String mk, String ref, String design) {
        for (int i = 0; i < lista.size(); i++) {
            QueryRow queryRow = lista.get(i);
            @SuppressWarnings("unchecked")
            List<String> keys = (List<String>) queryRow.getKey();
            String bostamp_ = keys.get(0);
            String dim_ = keys.get(1);
            String mk_ = keys.get(2);
            String ref_ = keys.get(3);
            String design_ = keys.get(4);
            if (bostamp_.equals(bostamp)) {
                if (dim_.equals(dim)) {
                    if (mk_.equals(mk)) {
                        if (ref_.equals(ref)) {
                            if (design_.equals(design)) {
                                Log.i(TAG, "Actualizar quantidade produzida na posição " + i);
                                notifyItemChanged(i);
                            }
                        }
                    }
                }
            }
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tv_ref;
        private final TextView tv_qtt;
        private final TextView tv_dim;
        private final LinearLayout llinha;
        private final Context contextHolder;

        //        public ViewHolder(View itemView, int ViewType) {
        public ViewHolder(View itemView) {
            super(itemView);
            llinha = (LinearLayout) itemView.findViewById(R.id.llinha);

            tv_ref = (TextView) itemView.findViewById(R.id.tv_ref);
            tv_qtt = (TextView) itemView.findViewById(R.id.tv_qtt);
            tv_dim = (TextView) itemView.findViewById(R.id.tv_dim);
            contextHolder = activityDossier;
        }
    }

    @SuppressLint("SetTextI18n")
    public void pintarObjecto(ViewHolder holder, int qttTotal, int qttParcial) {
        SharedPreferences prefs = MrApp.getPrefs();
        final boolean vertudo = prefs.getBoolean(Constantes.PREF_MOSTRAR_TODAS_LINHAS_PROD, true);
        holder.tv_qtt.setText(qttTotal + (qttParcial == 0 ? "" : "-" + qttParcial + "=" + (qttTotal - qttParcial)));
        holder.llinha.setBackgroundColor(ContextCompat.getColor(holder.contextHolder, R.color.md_white_1000));
        if (qttParcial > 0)
            holder.llinha.setBackgroundColor(ContextCompat.getColor(holder.contextHolder, R.color.md_amber_200));
        holder.llinha.setVisibility(View.VISIBLE);
        if (qttTotal - qttParcial == 0) {
            if (vertudo) {
                holder.llinha.setBackgroundColor(ContextCompat.getColor(holder.contextHolder, R.color.md_amber_900));
            } else {
                removerItem(holder.getAdapterPosition());
            }
        }
    }

    private class TaskQtd extends AsyncTask<Void, Void, Void> {
        private final QueryRow queryRow;
        private final ViewHolder holder;
        private int qtt;
        private int qttFeita;

        public TaskQtd(ViewHolder viewHolder) {
            this.queryRow = getItem(viewHolder.getAdapterPosition());
            this.holder = viewHolder;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            @SuppressWarnings("unchecked")
            List<String> keys = (List<String>) queryRow.getKey();
            String bostamp = keys.get(0);
            String dim = keys.get(1);
            String mk = keys.get(2);
            String ref = keys.get(3);
            String design = keys.get(4);
            qtt = ServicoCouchBase.getInstancia().getQttAgrupadasOSBI(bostamp, dim, mk, ref, design);
            qttFeita = ServicoCouchBase.getInstancia().getPecasOSPROD(bostamp, dim, mk, ref, design);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            pintarObjecto(holder, qtt, qttFeita);
        }
    }

    public void removerItem(int position) {
        if (position >= 0 && position <= lista.size()) {
            lista.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, lista.size());
        }
    }
}
