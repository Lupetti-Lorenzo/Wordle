import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SecretWordSessionManager extends Thread {
    private final List<String> parole;
    private final ScheduledExecutorService executor;
    private final SecretWordSession session; // sessione corrente
    public static int wordDuration; // durata della sessione in secondi
    // codici dei background per creare le risposte alle parole
    public static final String GREEN_BACKGROUND = "\u001B[42m";
    public static final String YELLOW_BACKGROUND = "\u001B[43m";
    public static final String ANSI_END = "\u001B[0m";

    public SecretWordSessionManager(List<String> parole, int seconds) {
        session = new SecretWordSession();
        this.parole = parole;
        wordDuration = seconds;
        // ogni tot secondi parte e cambia la secretword, sersessionmap e activewordtime initialDelay = 0 almeno parte anche subito
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(new SecretWordChanger(parole, session), 0, wordDuration, TimeUnit.SECONDS);
    }

    // usata per sapere se una parola è valida
    public boolean wordExists(String word) {
        return this.parole.contains(word);
    }

    // funzione prende la parola da provare,
    // restituisce la parola colorata in base alla parola segreta
    public String getWordHint(String word) {
        StringBuilder hints = new StringBuilder();
        String secWord = this.getSecretWord();
        for (int i = 0; i < secWord.length(); i++) {
            char currChar = word.charAt(i);
            String newChar;
            if (secWord.charAt(i) == currChar) { // lettera nella posizione giusta
                newChar = GREEN_BACKGROUND + currChar + ANSI_END;
            }
            else if (secWord.indexOf(currChar) != -1) { // lettera ce ma non nella posizione i
                newChar = YELLOW_BACKGROUND + currChar + ANSI_END;
            }
            else  { // lettera non ce
                newChar = String.valueOf(currChar);
            }
            hints.append(newChar);
        }
        return hints.toString();
    }

    // come getWordHint ma al posto delle lettere mette _, usata per la notifica
    public String getWordHintBG(String word) {
        StringBuilder hintsBG = new StringBuilder();
        String secWord = this.getSecretWord();
        for (int i = 0; i < secWord.length(); i++) {
            char currChar = word.charAt(i);
            String newChar;
            if (secWord.charAt(i) == currChar) { // lettera nella posizione giusta
                newChar = GREEN_BACKGROUND + "_" + ANSI_END;
            }
            else if (secWord.indexOf(currChar) != -1) { // lettera ce ma non nella posizione i
                newChar = YELLOW_BACKGROUND + "_" + ANSI_END;
            }
            else  { // lettera non ce
                newChar = "_";
            }
            hintsBG.append(newChar);
        }
        return hintsBG.toString();
    }

    // crea una nuova sessione di un utente e la ritona al chiamante
    public UserSession newUserSession(String username) {
        session.usersSessionMap.put(username, new UserSession(username));
        return this.getUserSession(username);
    }

    public UserSession getUserSession(String username) {
        return session.usersSessionMap.get(username);
    }

    // rimuovi una sessione dalla mappa, usata alla logout se non ho vinto o mandato 12 parole
    public void removeUserSession(String username) {
        session.usersSessionMap.put(username, null);
    }

    // se l'utente non nella mappa(mai entrato in questa sessione) oppure non ha finito, allora puó giocare
    public boolean canPlay(String username) {
        return !session.usersSessionMap.containsKey(username) ||
                !session.usersSessionMap.get(username).hasFinished();
    }

    public String getSecretWord() {
        return session.secretWord.toString();
    }

    public boolean isSecretWord(String word) {
        return word.equals(this.getSecretWord());
    }

    public String getWordDurationDate() { return this.session.wordDurationDate.toString(); }

    public void stopSWSM() { // usata dal ServerTerminationHandler per chiudere questo thread
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2000, TimeUnit.MILLISECONDS))
                executor.shutdownNow();
        }
        catch (InterruptedException e) {executor.shutdownNow();}
        System.out.println("[SERVER] SecretWordSessionManager terminato");
    }

    // thread che cambia la parola segreta ogni tot tempo
    private static class SecretWordChanger extends Thread {
        private final List<String> parole; // lista di parole da dove prendere la nuova secretword
        private final SecretWordSession session;
        public SecretWordChanger(List<String> parole, SecretWordSession session) {
            this.parole = parole;
            this.session = session;
        }

        @Override
        public void run() {
            int rand = (int) ((Math.random() * this.parole.size()) + 1); // indice dell'array delle parole random
            // nuova parola
            session.secretWord = new StringBuilder(parole.get(rand));
            // nuova sessione di utenti
            session.usersSessionMap = new HashMap<>();
            // nuovo identificativo della parola, il range di tempo espresso in datainizio-datafine
            session.wordDurationDate = new StringBuilder();
            // mi salvo l'intervallo di tempo in cui questa parola è attiva come range di date
            DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            Date today = Calendar.getInstance().getTime(); // data di adesso
            Calendar calendar = Calendar.getInstance(); // calcolo datafine
            calendar.setTime(today);
            calendar.add(Calendar.SECOND, wordDuration); // data fine parola segreta (data di adesso + durata)
            session.wordDurationDate.append(df.format(today)).append(" - ").append(df.format(calendar.getTime()));
            System.out.println("Parola cambiata: " + session.secretWord + " e durerà: " +  session.wordDurationDate);
        }
    }
}
