import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UsersDataJsonWriter {
    //singleton - interagisco con il file json tramite il singleton, che avendo il metodo synchronized lo fa eseguire a un thread alla volta, cosi sono sicuro che al file json ci accede un thread per volta
    private static final String usersJsonFileName = "./usersData.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static UsersDataJsonWriter INSTANCE;
    private UsersDataJsonWriter() {}

    public static UsersDataJsonWriter getINSTANCE() {
        if (INSTANCE == null)
            INSTANCE = new UsersDataJsonWriter();
        return INSTANCE;
    }

    // metodo per leggere la mappa e scrivere il file in maniera concorrente
    public synchronized int writeJson(Map<String, UserData> map, UserData data) {
        map.put(data.username, data);
        try (Writer fw = new FileWriter(usersJsonFileName)) {
            gson.toJson(map, fw);
        } catch (IOException e) {e.printStackTrace(); return 3;} // errore di scrittura sul json
        return 0;
    }

    // metodo per leggere la mappa dal file e ritornarla, usato dal server all'avvio
    public synchronized ConcurrentHashMap<String, UserData> readJsonMap() {
        try (JsonReader reader = new JsonReader(new FileReader(usersJsonFileName))) {
            ConcurrentHashMap<String, UserData> usersMap;
            usersMap = gson.fromJson(reader, new TypeToken<ConcurrentHashMap<String, UserData>>() {
            }.getType());
            return usersMap;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
