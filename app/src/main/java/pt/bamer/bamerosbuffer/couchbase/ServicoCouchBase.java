package pt.bamer.bamerosbuffer.couchbase;


import android.content.Context;
import android.support.v7.app.AlertDialog;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.Reducer;
import com.couchbase.lite.View;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.auth.Authenticator;
import com.couchbase.lite.auth.AuthenticatorFactory;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import pt.bamer.bamerosbuffer.MrApp;
import pt.bamer.bamerosbuffer.utils.Constantes;
import pt.bamer.bamerosbuffer.utils.ValoresDefeito;

public class ServicoCouchBase {
    private static final String TAG = ServicoCouchBase.class.getSimpleName();
    public final int VERSAO_INTERNA_DAS_VIEWS = 12;

    public static final String DOC_TIPO_OSBO = "osbo";
    public static final String DOC_TIPO_OSBI = "osbi";
    public static final String DOC_TIPO_OSPROD = "osprod";
    public static final String DOC_TIPO_TIMER = "ostimer";

    private static final String COUCH_DB_NAME = "bameros001";
    private static final String COUCH_USER_ID = "syncandroid";
    private static final String COUCH_USER_PASSWORD = "syncandroid";
    //    public static final String COUCH_SERVER_URL = "http://server.bamer.pt:4984";
    public static final String COUCH_SERVER_URL = "http://192.168.0.3:4984";
    public static final String COUCH_SERVER_AND_DB_URL = COUCH_SERVER_URL + "/" + COUCH_DB_NAME + "/";

    private static ServicoCouchBase instancia;
    private Database database;
    private Manager manager;
    private Replication pullReplication;

    public View viewOS_CABOrderCorteOrdemBostampCor;
    public View viewLinhasDoBostamp;
    private View viewPecasPorDossier;
    private View viewOSPRODpecasFeitasPorDossier;
    public View viewTempos;
    public View viewDistinctMaquinas;
    public View viewOSPROD;
    public View viewLinhasOSBIQttAGrupada;
    private View viewQtdAgrupadaOSBI;
    private View viewOSPRODpecasFeitasAgrupadas;
    public View viewTempoUnixMaquina;

    public View viewOS_CABODtcorteFBostamp;
    private MrApp mrApp;

    public static ServicoCouchBase getInstancia() {
        if (instancia == null) {
            instancia = new ServicoCouchBase();
        }

        return instancia;
    }

    public void start(final Context context) {
        mrApp = (MrApp) context.getApplicationContext();
        URL syncUrl;
        try {
            syncUrl = new URL(COUCH_SERVER_AND_DB_URL);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        try {
            manager = new Manager(new AndroidContext(mrApp), Manager.DEFAULT_OPTIONS);
            database = manager.getDatabase(COUCH_DB_NAME);

            setupViews();

            pullReplicatorCriar(syncUrl);

            Log.i(TAG, "Manager COUCHBASE iniciou com sucesso, com os canais " + pullReplication.getChannels().toString());

        } catch (IOException | CouchbaseLiteException e) {
            e.printStackTrace();
            AlertDialog alert = new AlertDialog.Builder(context)
                    .setCancelable(true)
                    .setMessage("Ocorreu um erro ao iniciar o serviço COUCHBASE")
                    .setTitle("ERRO")
                    .setNeutralButton("OK", null)
                    .create();
            alert.show();
        }
    }

    private void pullReplicatorCriar(URL syncUrl) {
        Authenticator basicAuthenticator = AuthenticatorFactory.createBasicAuthenticator(COUCH_USER_ID, COUCH_USER_PASSWORD);

        pullReplication = database.createPullReplication(syncUrl);
        pullReplication.setContinuous(true);
        pullReplication.setAuthenticator(basicAuthenticator);

        String estado = MrApp.getPrefs().getString(Constantes.PREF_ESTADO, ValoresDefeito.ESTADO);
        List<String> canais = new ArrayList<>();
        canais.add(estado.trim());
        canais.add(DOC_TIPO_OSPROD);
        canais.add(DOC_TIPO_TIMER);
        pullReplication.setChannels(canais);

        Replication.ChangeListener changeListener = new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Replication replication = event.getSource();
                if (replication.getCompletedChangesCount() != replication.getChangesCount()) {
                    Log.v(TAG, "Alteração pullReplication " + replication.getCompletedChangesCount() + "/" + replication.getChangesCount() + " -> " + replication.getStatus().toString());
                    mrApp.updateSyncProgress(
                            replication.getCompletedChangesCount(),
                            replication.getChangesCount(),
                            replication.getStatus()
                    );
                }
            }
        };
        pullReplication.addChangeListener(changeListener);

        pullReplication.start();
    }

    public void stop() {
        if (pullReplication != null) {
            pullReplication.stop();
        }
        pullReplication = null;
        database.close();
        manager.close();
        Log.w(TAG, "Terminou os serviços COUCHBASE");
    }

    private void setupViews() {
        Log.i(TAG, "Versão da Views: " + getVersao());

        viewOS_CABOrderCorteOrdemBostampCor = database.getView("oscabCorte");
        Mapper mapper = new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                if (DOC_TIPO_OSBO.equals(document.get(CamposCouch.FIELD_TIPO))
                        && document.get(CamposCouch.FIELD_ESTADO).equals(MrApp.getEstado())
                        && document.get(CamposCouch.FIELD_SECCAO).equals(MrApp.getSeccao())
                        && !document.get(CamposCouch.FIELD_ORDEM).equals(0)
                        ) {
                    ArrayList<Object> listaDeKeys = new ArrayList<>();
                    listaDeKeys.add(document.get(CamposCouch.FIELD_DTCORTEF));
                    listaDeKeys.add(document.get(CamposCouch.FIELD_ORDEM));
                    listaDeKeys.add(document.get(CamposCouch.FIELD_BOSTAMP));
                    listaDeKeys.add(document.get(CamposCouch.FIELD_COR));

                    emitter.emit(listaDeKeys, document);
                }
            }
        };
        viewOS_CABOrderCorteOrdemBostampCor.setMap(mapper, getVersao());

        viewOS_CABODtcorteFBostamp = database.getView("viewOS_CABODtcorteFBostamp");
        mapper = new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                if (DOC_TIPO_OSBO.equals(document.get(CamposCouch.FIELD_TIPO))
                        && document.get(CamposCouch.FIELD_ESTADO).equals(MrApp.getEstado())
                        && document.get(CamposCouch.FIELD_SECCAO).equals(MrApp.getSeccao())
                        && !document.get(CamposCouch.FIELD_ORDEM).equals(0)
                        ) {
                    List<Object> keys = new ArrayList<>();
                    keys.add(document.get(CamposCouch.FIELD_DTCORTEF));
                    keys.add(document.get(CamposCouch.FIELD_BOSTAMP));
                    emitter.emit(keys, document);
                }
            }
        };
        viewOS_CABODtcorteFBostamp.setMap(mapper, getVersao());

        viewLinhasDoBostamp = database.getView("view_linhas_os");
        Mapper mapperOSBO = new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                if (DOC_TIPO_OSBI.equals(document.get(CamposCouch.FIELD_TIPO))
                        ) {
                    Object bostamp = document.get(CamposCouch.FIELD_BOSTAMP);
                    Object dim = document.get(CamposCouch.FIELD_DIM);
                    Object design = document.get(CamposCouch.FIELD_DESIGN);
                    emitter.emit(Arrays.asList(bostamp, dim, design), document);
                }
            }
        };
        viewLinhasDoBostamp.setMap(mapperOSBO, getVersao());

        viewOSPROD = database.getView("viewOSPROD");
        mapper = new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                if (DOC_TIPO_OSPROD.equals(document.get(CamposCouch.FIELD_TIPO))) {
                    Object dim = document.get(CamposCouch.FIELD_DIM);
                    Object mk = document.get(CamposCouch.FIELD_MK);
                    Object ref = document.get(CamposCouch.FIELD_REF);
                    Object design = document.get(CamposCouch.FIELD_DESIGN);
                    Object bostamp = document.get(CamposCouch.FIELD_BOSTAMP);
                    emitter.emit(Arrays.asList(bostamp, dim, mk, ref, design), document);
                }
            }
        };
        viewOSPROD.setMap(mapper, getVersao());

        viewPecasPorDossier = database.getView("pecasPorDossier");
        viewPecasPorDossier.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (DOC_TIPO_OSBI.equals(document.get(CamposCouch.FIELD_TIPO))
                        ) {
                    emitter.emit(document.get(CamposCouch.FIELD_BOSTAMP), document.get(CamposCouch.FIELD_QTT));
                }
            }
        }, new Reducer() {
            @Override
            public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                int total = 0;
                for (Object value : values) {
                    int newVal;
                    if (value instanceof Double) {
                        newVal = ((Double) value).intValue();
                    } else {
                        newVal = (int) value;
                    }
                    total += newVal;
                }
                return total;
            }
        }, getVersao());


        viewOSPRODpecasFeitasPorDossier = database.getView("pecasFeitasPorDossier");
        viewOSPRODpecasFeitasPorDossier.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (DOC_TIPO_OSPROD.equals(document.get(CamposCouch.FIELD_TIPO))
                        && (int) document.get(CamposCouch.FIELD_QTT) != 0
                        ) {
                    emitter.emit(document.get(CamposCouch.FIELD_BOSTAMP), document.get(CamposCouch.FIELD_QTT));
                }
            }
        }, new Reducer() {
            @Override
            public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                int total = 0;
                for (Object value : values) {
                    int newVal;
                    if (value instanceof Double) {
                        newVal = ((Double) value).intValue();
                    } else {
                        newVal = (int) value;
                    }
                    total += newVal;
                }
                return total;
            }
        }, getVersao());

        viewOSPRODpecasFeitasAgrupadas = database.getView("pecasFeitasPorAgrupamento");
        viewOSPRODpecasFeitasAgrupadas.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (DOC_TIPO_OSPROD.equals(document.get(CamposCouch.FIELD_TIPO))
                        && (int) document.get(CamposCouch.FIELD_QTT) != 0
                        ) {
                    Object dim = document.get(CamposCouch.FIELD_DIM);
                    Object mk = document.get(CamposCouch.FIELD_MK);
                    Object ref = document.get(CamposCouch.FIELD_REF);
                    Object design = document.get(CamposCouch.FIELD_DESIGN);
                    Object bostamp = document.get(CamposCouch.FIELD_BOSTAMP);
                    emitter.emit(Arrays.asList(bostamp, dim, mk, ref, design), document.get(CamposCouch.FIELD_QTT));
                }
            }
        }, new Reducer() {
            @Override
            public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                int total = 0;
                for (Object value : values) {
                    int newVal;
                    if (value instanceof Double) {
                        newVal = ((Double) value).intValue();
                    } else {
                        newVal = (int) value;
                    }
                    total += newVal;
                }
                return total;
            }
        }, getVersao());

        viewLinhasOSBIQttAGrupada = database.getView("viewLinhasOSBIQttAGrupada");
        mapper = new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (DOC_TIPO_OSBI.equals(document.get(CamposCouch.FIELD_TIPO))
                        ) {
                    Object bostamp = document.get(CamposCouch.FIELD_BOSTAMP);
                    Object ref = document.get(CamposCouch.FIELD_REF);
                    Object design = document.get(CamposCouch.FIELD_DESIGN);
                    Object dim = document.get(CamposCouch.FIELD_DIM);
                    Object mk = document.get(CamposCouch.FIELD_MK);
                    emitter.emit(Arrays.asList(bostamp, dim, mk, ref, design), document);
                }
            }
        };
        viewLinhasOSBIQttAGrupada.setMap(mapper, getVersao());

        viewQtdAgrupadaOSBI = database.getView("viewQtdAgrupadaOSBI");
        viewQtdAgrupadaOSBI.setMapReduce(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (DOC_TIPO_OSBI.equals(document.get(CamposCouch.FIELD_TIPO))
                        ) {
                    Object bostamp = document.get(CamposCouch.FIELD_BOSTAMP);
                    Object dim = document.get(CamposCouch.FIELD_DIM);
                    Object mk = document.get(CamposCouch.FIELD_MK);
                    Object ref = document.get(CamposCouch.FIELD_REF);
                    Object design = document.get(CamposCouch.FIELD_DESIGN);
                    emitter.emit(Arrays.asList(bostamp, dim, mk, ref, design), document.get(CamposCouch.FIELD_QTT));
                }
            }
        }, new Reducer() {
            @Override
            public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                int total = 0;
                for (Object value : values) {
                    int newVal;
                    if (value instanceof Double) {
                        newVal = ((Double) value).intValue();
                    } else {
                        newVal = (int) value;
                    }
                    total += newVal;
                }
                return total;
            }
        }, getVersao());

        viewTempos = database.getView("view_tempos_dossiers");
        mapperOSBO = new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {

                Object seccaoDoc = document.get(CamposCouch.FIELD_SECCAO);
                if (DOC_TIPO_TIMER.equals(document.get(CamposCouch.FIELD_TIPO))) {
                    if (seccaoDoc == null) {
                        Log.e(TAG, "O tempo " + document.get(CamposCouch.FIELD_BOSTAMP) + " tem secção NULA!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        return;
                    }
                }
                if (DOC_TIPO_TIMER.equals(document.get(CamposCouch.FIELD_TIPO))
                        && document.get(CamposCouch.FIELD_ESTADO).equals(MrApp.getEstado())
                        && document.get(CamposCouch.FIELD_SECCAO).equals(MrApp.getSeccao())
                        ) {
                    emitter.emit(Arrays.asList(
                            document.get(CamposCouch.FIELD_BOSTAMP)
                            , document.get(CamposCouch.FIELD_UNIXTIME)
                            , document.get(CamposCouch.FIELD_LASTTIME)
                    ), document);
                }
            }
        };
        viewTempos.setMap(mapperOSBO, getVersao());

        viewTempoUnixMaquina = database.getView("viewTempoUnixMaquina");
        mapper = new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (DOC_TIPO_TIMER.equals(document.get(CamposCouch.FIELD_TIPO))
                        && document.get(CamposCouch.FIELD_ESTADO).equals(MrApp.getEstado())
                        && MrApp.getSeccao().equals(document.get(CamposCouch.FIELD_SECCAO))
                        ) {
                    emitter.emit(
                            Arrays.asList(document.get(CamposCouch.FIELD_MAQUINA), document.get(CamposCouch.FIELD_UNIXTIME))
                            , document);
                }
            }
        };
        viewTempoUnixMaquina.setMap(mapper, getVersao());

        viewDistinctMaquinas = database.getView("view_maquinas");
        mapper = new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                if (document.get(CamposCouch.FIELD_SECCAO) == null) {
                    return;
                }
                if (DOC_TIPO_TIMER.equals(document.get(CamposCouch.FIELD_TIPO))
                        && document.get(CamposCouch.FIELD_SECCAO).equals(MrApp.getSeccao())
                        ) {
                    Object maquina = document.get(CamposCouch.FIELD_MAQUINA);
                    emitter.emit(maquina, null);
                }
            }
        };
        viewDistinctMaquinas.setMap(mapper, getVersao());
    }

    private String getVersao() {
        int versaoPublica = MrApp.getPrefs().getInt(Constantes.PREF_VERSAO_VIEWS_COUCHBASE, 1);
        return versaoPublica + "." + VERSAO_INTERNA_DAS_VIEWS;
    }

    //    //todo em produção tentar retirar para melhorar consistência da versão
    @SuppressWarnings({"unused"})
    private String getVersao(String versao_da_build) {
        int versaoPublica = MrApp.getPrefs().getInt(Constantes.PREF_VERSAO_VIEWS_COUCHBASE, 1);
        return versaoPublica + "." + versao_da_build;
    }

//    }

    public int getPecasPorOS(String bostamp) {
        Query queryPorData = viewPecasPorDossier.createQuery();
        queryPorData.setStartKey(bostamp);
        queryPorData.setEndKey(bostamp);
        int somaQtt = 0;
        try {
            QueryEnumerator queryEnumerator = queryPorData.run();
            while (queryEnumerator.hasNext()) {
                QueryRow queryRow = queryEnumerator.next();
                somaQtt = (int) queryRow.getValue();
            }
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "getPecasPorOS(" + bostamp + ") = " + somaQtt);
        return somaQtt;
    }

    public int getPecasFeitasPorOS(String bostamp) {
        Query query = viewOSPRODpecasFeitasPorDossier.createQuery();
        query.setStartKey(bostamp);
        query.setEndKey(bostamp);
        int somaQtt = 0;
        try {
            QueryEnumerator queryEnumerator = query.run();
            while (queryEnumerator.hasNext()) {
                QueryRow queryRow = queryEnumerator.next();
                somaQtt = (int) queryRow.getValue();
            }
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        return somaQtt;
    }

    @SuppressWarnings("unused")
    public int getPecasOSPROD(String bostamp, String dim, String mk, String ref, String design) {
        Query query = viewOSPRODpecasFeitasAgrupadas.createQuery();
        query.setStartKey(Arrays.asList(bostamp, dim, mk, ref, design));
        query.setEndKey(Arrays.asList(bostamp, dim, mk, ref, design));
        int qtt = 0;
        try {
            QueryEnumerator queryEnumerator = query.run();
            for (QueryRow queryRow : queryEnumerator) {
                int q = (int) queryRow.getValue();
                qtt += q;
            }
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "getPecasOSPROD(" + bostamp + ", " + dim + ", " + mk + ", " + ref + ", " + design + ") = " + qtt);
        return qtt;
    }

    public int getPecasOSPROD(String bostamp) {
        Query query = viewOSPRODpecasFeitasAgrupadas.createQuery();
        query.setStartKey(Arrays.asList(bostamp, "", "", "", ""));
        query.setEndKey(Arrays.asList(bostamp, "Z", "Z", "Z", "Z"));
        int qtt = 0;
        try {
            QueryEnumerator queryEnumerator = query.run();
            for (QueryRow queryRow : queryEnumerator) {
                int q = (int) queryRow.getValue();
                qtt += q;
            }
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "getPecasOSPROD(" + bostamp + ") = " + qtt);
        return qtt;
    }

    public long getTempoTotal(String bostamp) {
        com.couchbase.lite.View view = ServicoCouchBase.getInstancia().viewTempos;
        Query query = view.createQuery();
        String maxText = Long.MAX_VALUE + "";
        query.setStartKey(Arrays.asList(bostamp, "", "", MrApp.getSeccao()));
        query.setEndKey(Arrays.asList(bostamp, maxText, maxText, MrApp.getSeccao()));
        long tempoCalculado = 0;
        try {
            QueryEnumerator queryEnumerator = query.run();
            Document document;
            while (queryEnumerator.hasNext()) {
                QueryRow queryRow = queryEnumerator.next();
                document = queryRow.getDocument();
                if (Integer.parseInt(document.getProperty(CamposCouch.FIELD_POSICAO).toString()) == 2) {
                    long inicio = Long.parseLong(document.getProperty(CamposCouch.FIELD_LASTTIME).toString());
                    long fim = Long.parseLong(document.getProperty(CamposCouch.FIELD_UNIXTIME).toString());
                    tempoCalculado += (fim - inicio);
//                    Log.i(TAG, bostamp + ", Tempo Total Calculado (TTC): " + tempoCalculado);
                }
            }
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        return tempoCalculado;
    }

    public Document getUltimoTempo(String bostamp) {
        com.couchbase.lite.View view = ServicoCouchBase.getInstancia().viewTempos;
        Query query = view.createQuery();
        String maxText = Long.MAX_VALUE + "";
        query.setEndKey(Arrays.asList(bostamp, "", ""));
        query.setStartKey(Arrays.asList(bostamp, maxText, maxText));
        query.setDescending(true);
        query.setLimit(1);
        Document document = null;
        try {
            QueryEnumerator queryEnumerator = query.run();
            while (queryEnumerator.hasNext()) {
                QueryRow queryRow = queryEnumerator.next();
                document = queryRow.getDocument();
            }

        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        return document;
    }

    @SuppressWarnings("unused")
    public int getQttAgrupadasOSBI(String bostamp, String dim, String mk, String ref, String design) {
        Query query = viewQtdAgrupadaOSBI.createQuery();
        query.setStartKey(Arrays.asList(bostamp, dim, mk, ref, design));
        query.setEndKey(Arrays.asList(bostamp, dim, mk, ref, design));
        int qtt = 0;
        try {
            QueryEnumerator queryEnumerator = query.run();
            while (queryEnumerator.hasNext()) {
                int q = (int) queryEnumerator.next().getValue();
                qtt += q;
            }

        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        return qtt;
    }

    public Database getDatabase() {
        return database;
    }

    public Document getBo(String bostamp) throws CouchbaseLiteException {
        Query query = viewOS_CABODtcorteFBostamp.createQuery();
        query.setStartKey(Arrays.asList("", bostamp));
        query.setEndKey(Arrays.asList("Z", bostamp));
        QueryEnumerator queryEnumerator = query.run();
        Document doc = null;
        Log.i(TAG, "getBo('" + bostamp + "') retornou " + queryEnumerator.getCount() + " com a key " + query.getStartKey().toString() + " <-> " + query.getEndKey().toString());
        for (int i = 0; i < queryEnumerator.getCount(); i++) {
            doc = queryEnumerator.getRow(i).getDocument();
            Log.d(TAG, queryEnumerator.getRow(i).toString());
        }
        return doc;
    }

    public Replication getPullReplication() {
        return pullReplication;
    }
}
