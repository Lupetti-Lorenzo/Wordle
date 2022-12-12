import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

// thread per gestire la connessione con un client
public class ClientHandler implements Runnable {
    // mappa degli utenti registrati
    private final ConcurrentHashMap<String, UserData> usersMap;
    private final SecretWordSessionManager sessionManager; // per interagire con la parola segreta e quindi sapere se esiste, prendere gli hints...
    // CONNECTION DATA
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final MulticastSocket ms;

    //SESSION DATA - stato dell'applicazione
    private UserData userData;
    private UserSession userSession;

    public ClientHandler(Socket socket, ConcurrentHashMap<String, UserData> usersMap, SecretWordSessionManager wsManager, MulticastSocket ms) throws IOException {
        this.socket = socket;
        this.usersMap = usersMap;
        this.sessionManager = wsManager;
        this.ms = ms;
        this.in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void run() {
            boolean end = false;
            // loop principale del client handler, riceve comandi, agiscono in base allo stato (userSession e userData)
            // esce se l'utente vuole uscire oppure se ce stato un errore di I/O in register/login/sendWord che sono le uniche funzioni che possono generarlo
            while (!end) {
                String choice = readUserInput();
                if (choice == null) break; // client ha chiuso "brutalmente"
                switch (choice) {
                    case "register" -> end = register();
                    case "login" -> end = login();
                    case "logout" -> logout();
                    case "playWORDLE" -> playWordle();
                    case "sendWord" -> end = sendWord();
                    case "sendMeStatistics" -> sendMeStatistics();
                    case "share" -> share();
                    default -> {
                        end = true;
                        System.out.println("[SERVER] Fine comunicazione con un client, thread finished");
                    }
                }
            }
        try {
            // chiudo la connessione
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            System.out.println("[SERVER] Errore durante chiusura della comunicazione  "+(userData == null ? "con un thread non loggato":"col client "+userData.username)+", thread finithed");
        }
        // programma terminato
    }

    // playWordle() inizia o riprende una sessione (settando userSession), e se puo giocare manda 0 al client
    private void playWordle() {
        // se non sono loggato mando messaggio di errore 1
        if (this.userData == null) {
            out.println("1"); // non sei loggato
            return;
        }
        if (!sessionManager.canPlay(this.userData.username)) { // se ho gia giocato o ha vinto o finito i tentativi
            out.println("2"); // non puoi giocare
            return;
        }
        // se non cè una sessione in corso la creo, altrimenti l'ho già o la prendo dalla mappa
        userSession = sessionManager.getUserSession(userData.username);
        if (userSession == null) { // prima prova per questa sessione
            userSession = sessionManager.newUserSession(this.userData.username);
        }
        out.println("0"); //puoi iniziare
    }


    private boolean sendWord() {
        // prima di tutto il server mi dice se posso mandare la parola
        if (userData == null || sessionManager.getUserSession(userData.username) == null) {
            out.println("1"); // la parola è cambiata nel corso della sessione
            return false;
        }// sessione cambiata mentre inviava parole
        if (userSession.hasFinished()) {
            out.println("2"); // non puoi piu mandare parole
            return false;
        } else out.println("0"); // gli dico che puo inviare

        // leggo la parola dal client
        String tryWord = readUserInput();
        if (tryWord == null) return true; // errore - chiudi il thread
        if (tryWord.equals("EXIT")) { // se exit non fo nulla

        } else if (!sessionManager.wordExists(tryWord)) { // parola non esistente, mando messaggio di parola non trovata
            out.println("noWord");
        } else if (sessionManager.isSecretWord(tryWord)) { // la parola esiste ed è proprio la secret word
            out.println("secretWord"); // comunico l'esito al client
            out.println(this.userSession.nextHint(sessionManager.getWordHint(tryWord))); // aggiorno gli hints della parola e li mando all'utente
            this.userSession.nexHintBG(sessionManager.getWordHintBG(tryWord)); // aggiorno il messaggio di notifica
            userSession.addWordCount();
            userSession.setWinner(true);
            // partita finita, vedo se vuole condividere la vittoria
            String condividi = readUserInput();
            if (condividi == null) return true; // errore - chiudi il thread
            if (condividi.equals("share")) {
                share();
            }
            // aggiorno le statistiche dell' utente
            userData.guessDistribution[userSession.getWordCount()]++; // aggiorno guess distribution +1 al punto del numero dei tentativi
            userData.streakVittorie++;
            userData.partiteGiocate++;
            userData.partiteVinte++;
            if (userData.streakVittoreMax < userData.streakVittorie)
                userData.streakVittoreMax = userData.streakVittorie;
            // aggiorno il file json
            UsersDataJsonWriter.getINSTANCE().writeJson(usersMap, userData);
        } else {  // parola esistente, non è la secret word, mando la hint
            // setto gli hints e li mando al client
            out.println("validWord");
            out.println(this.userSession.nextHint(sessionManager.getWordHint(tryWord))); // aggiorno e mando hints
            this.userSession.nexHintBG(sessionManager.getWordHintBG(tryWord)); // aggiorno il messaggio di notifica
            userSession.addWordCount(); // +1 alle parole provate
            out.println(userSession.getWordCount()); // mando il word count per far sapere al client a che punto è
            // se ha provato 12 parole
            if (userSession.getWordCount() >= UserSession.MAX_TRIES) { // ultima parola, non ha vinto
                // aggiorno le statistiche dell' utente
                userData.partiteGiocate++;
                // aggiorno la mappa e il file json
                UsersDataJsonWriter.getINSTANCE().writeJson(usersMap, userData);
            }
        }
        return false;
    }

    // register() - riceve username e password, l'input e aggiorna il json con il nuovo utente
    // comunica al client 0 se ok, 1 se password vuota, 2 se user gia esistente, 3 se errore di scrittura sul json
    private boolean register() { // messaggio di errore - 0: ok - 1 passwordvuota - 2 usergiaesistente
            int err;
            // ricevo username e password
            String username = readUserInput();
            if (username == null) return true;
            String password = readUserInput();
            if (password == null) return true; // errore - chiudi il thread
            if (password.equals("")) err = 1; // password vuota
            else if (usersMap.containsKey(username)) err = 2; // user gia esistente
            else {
                // registro l'utente, lo metto nella mappa e la scrivo sul file - ritorna 3 se ce stato un errore, altrimenti 0
                err = UsersDataJsonWriter.getINSTANCE().writeJson(usersMap, new UserData(username, password));
               // else err = 0 - tutto ok
                if (err == 0) System.out.println("[SERVER] Nuovo utente registrato con username " + username);
            }
            out.println(err); // mando l'esito al client
            return false;
    }

    // login() - riceve username e password, controlla se esiste e se la password è corretta
    // comunica al client 0 se ok, 1 se password errata, 2 se user non esiste, 3 se e gia loggato
    private boolean login() {
        int err = 0;
        // ricevo username e password
        String username = readUserInput();
        if (username == null) return true;
        String password = readUserInput();
        if (password == null) return true; // errore - chiudi il thread
        if (this.userData != null)
            err = 3; // gia loggato
        else { // non loggato, faccio login
            this.userData = usersMap.get(username); // cerco lo user nella mappa
            if (userData == null) err = 1; // user non esiste
            else if (!userData.password.equals(password)) {
                err = 2;
                this.userData = null; // non mi sono loggato
            } // password errata
            else { // loggato con successo
                // else err = 0 - tutto ok
                System.out.println("[SERVER] Utente " + userData.username + " ha eseguito il login");
                out.println(err); // mando l'esito al client
                out.println(username); // mando l'username al client solo se si e loggato
                return false;
            }
        }
        out.println(err);
        return false;
    }

    //  effettua il logout dell’utente dal servizio.
    private void logout() {
        stopClientMNC(); // stoppo il realativo MNC del client
        // se ero in una sessione non finita, la cancello dalle sessioni
        System.out.println("[SERVER] Utente " + userData.username + " ha eseguito il logout");
        if (this.sessionManager.getUserSession(userData.username) != null && this.userSession != null && !this.userSession.hasFinished())
            sessionManager.removeUserSession(this.userData.username);
        this.userData = null;
        this.userSession = null;
    }

    // condividere i risultati della partita sul gruppo multicast
    // viene chiamata dopo che l'utente finisce la partita, se vuole condividerla
    private void share() {
        // se la partita l'ho vinta o non ho condiviso la vittoria, non posso condividerla
        if (userSession == null || userSession.isShared() || !userSession.isWinner()) {
            out.println("1"); // non puoi condividere
            return;
        }
        // condivido sul gruppo multicast
        sendDatagramToGroup(userSession.getWinNotification(sessionManager.getWordDurationDate()));
        // setto la variabile shared
        userSession.setShared();
        out.println("0"); // condivisione avvenuta con successo
    }

    // mando il pacchetto di terminazione del MNC di questo client
    private void stopClientMNC() {
        sendDatagramToGroup(this.userData.username+"&%#$");
    }
    //richiesta delle statistiche dall’utente aggiornata dopo l’ultimo gioco, gli invio la stringa con $ per poi formattarla lato client (per usare readLine una volta sola)
    private void sendMeStatistics() {
        out.println(userData.username + ", ecco le tue statistiche aggiornate all'ultima partita: $" +
                "PartiteGiocate: " + userData.partiteGiocate + "$" +
                "Percentuale vittoria: "+ (((double)userData.partiteVinte)/((double)userData.partiteGiocate))*100 + "%$" +
                "Streak vittorie in corso: " + userData.streakVittorie + "$" +
                "Streak vittore massima: " + userData.streakVittoreMax + "$" +
                "Punteggio WAS: " +String.format("%.3f", computeScoreWAS(userData.partiteGiocate, userData.guessDistribution)) + "$"); // il punteggio è double, prendo le 3 cifre decimali
    }

    // in base alle partite giocate e alla guess distribution, mi calcolo iil punteggio (WAS)
    public double computeScoreWAS(int numPlayed, int[] guessDist) {
        int sum = 0, numGuessed = 0;
        for (int i = 0; i < guessDist.length; i++) {
            sum += (i + 1) * guessDist[i];
            numGuessed += guessDist[i];
        }
        sum += (UserSession.MAX_TRIES + 1) * (numPlayed - numGuessed);
        return ((double) sum / (double) numPlayed);
    }

    private void sendDatagramToGroup(String message) {
        try {
            byte[] buffer = message.getBytes();
            InetAddress group = InetAddress.getByName(WordleServerMain.SERVERMULTICAST);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, WordleServerMain.PORTAMULTICAST);
            ms.send(packet);
        } catch (IOException e) {
            System.out.println("[SERVER] Errore nella sendDatagramToGroup(): " + e.getMessage());
        }
    }

    // funzione usata per leggere una stringa dall'utente, se ritorna null o lancia una eccezione allora c'è stato un errore e il chiamante esce dalla sessione
    private String readUserInput() {
        String err = "[SERVER] Errore durante la comunicazione "+(userData == null ? "con un client non loggato" : "col client "+userData.username)+", thread finished";
        try {
            String input = in.readLine();
            if (input == null) {
                System.out.println(err);
                userData = null;
            }
            return input;
        } catch (IOException e){};
        System.out.println(err);
        userData = null;
        return null;
    }
}
