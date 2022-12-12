import java.util.HashMap;
import java.util.Map;

public class SecretWordSession { // rappresenta la sessione specifica a una parola, quindi ha la parola segreta, la durata della sessione in data e la lista di utenti che hanno/stanno giocando
    public volatile StringBuilder wordDurationDate; // range in data della durata della parola
    public volatile StringBuilder secretWord; // parola segreta
    // mappa delle sessioni degli utenti
    // un utente crea una sessione per la parola corrente quando manda gioca per la prima volta
    public volatile Map<String, UserSession> usersSessionMap;

    public SecretWordSession() {
        secretWord = new StringBuilder();
        wordDurationDate = new StringBuilder();
        usersSessionMap = new HashMap<>();
    }
}
