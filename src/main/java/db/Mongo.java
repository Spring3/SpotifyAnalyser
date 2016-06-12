package db;

import com.mongodb.MongoClient;
import com.mongodb.MongoSocketException;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by spring on 6/12/16.
 */
public class Mongo {

    private Mongo(){

    }

    private static Mongo instance;
    private MongoClient client;
    private MongoDatabase db;
    private String uri;
    private int port;
    private String colName;

    public String getUri(){
        return uri;
    }

    public int getPort(){
        return port;
    }

    public String getColName(){
        return colName;
    }

    public void setUri(String uri){
        this.uri = uri;
    }

    public void setPort(int port){
        this.port = port;
    }

    public void setColName(String colName){
        this.colName = colName;
    }

    public static Mongo getInstance(){
        if (instance == null){
            instance = new Mongo();
        }
        return instance;
    }

    public void connect(String uri, int port, String colName) {
        setUri(uri);
        setPort(port);
        setColName(colName);

        client = new MongoClient(uri, port);
        db = client.getDatabase(colName);
    }

    public void connect(){
        assert uri != null;
        assert port != 0;
        assert colName != null;
        connect(getUri(), getPort(), getColName());
    }

    public void save(String[] jsons) {
        List<Document> documents = new ArrayList<>();
        for (String j : jsons) {
            try {
                Document doc = Document.parse(j);
                documents.add(doc);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        db.getCollection("track").insertMany(documents);

    }

}
