import java.io.*;
import java.net.Socket;
import java.util.*;



public class WordleClientMain {

    // Percorso del file di configurazione del client
    public static final String configFile = "./client.properties";
    // Lista delle notifiche inviate dagli altri utenti
    private static final List<String> notifiche = Collections.synchronizedList(new ArrayList<>());
    private static String username;
    private static BufferedReader keyboard; // per leggere input da tastiera dell'utente
    // background colorati per stampare messaggi di tipo diverso
    public static final String GREEN_BACKGROUND = "\u001B[42m";
    public static final String YELLOW_BACKGROUND = "\u001B[43m";
    public static final String RED_BACKGROUND = "\u001B[41m";
    public static final String ANSI_END = "\u001B[0m";

    // CONNECTION DATA
    private static String SERVER;
    private static int PORTASERVER;
    private static String SERVERMULTICAST;
    private static int PORTAMULTICAST;
    private static Socket socket;
    private static BufferedReader in;
    private static PrintWriter out;


    public static void main(String[] args) {
        // Leggo il file di configurazione, aggiorna indirizzoserver poratserver, servermulticast e portamulticast
        readConfig();
        try { // inizializzazione della connessione e delle risorse
            socket = new Socket(SERVER, PORTASERVER);
            keyboard = new BufferedReader(new InputStreamReader(System.in));
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
            exitApplication("nell'avvio dell'applicazione, il server potrebbe non essere in funzione: " + e.getMessage());
        }

        System.out.println("Benvenuto a WORDLE");
        // loop principale del gioco,
        boolean exit = false;
        while (!exit) { // loop principale dell'applicazione, che gestisce i 2 menu principali
            int loginDone;
            while ((loginDone = loginRegisterLoop()) == 1); // rimango nella fase di login/register finchè non sono loggato oppure inserisce esci
            if (loginDone == -1) break; // voleva uscire, non passo alla prossima fase e chiudo la connessione
            // sono loggato
            // mi unisco al gruppo multicast, faccio partire WordleClient.MulticastNotifyCollector - in parallelo, quando arriva una notifica la salva nella lista notifiche
            startMNC();
            exit = menu(); // true se user è voluto uscire, altrimenti false(logout) e ritorno alla login phase
        }
        try {
            // chiudo la connessione
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            exitApplication("(run) chiusura dell'applicazione");
        }
        //programma terminato
        System.out.println("Bye bye by WORDLE");
    }

    // FASE DI LOGIN/REGISTRAZIONE
    // usato nel loop iniziale, le opzioni sono login, registrazione ed esci.
    // ritorno -1 se lo user vuole uscire, 0 se login effettuato, 1 se ho fatto registrazione
    private static int loginRegisterLoop() {
        // chiedo all'utente un valore tra 1 e 3
        System.out.print("1) Login\n2) Registrazione\n3) Esci\nInserisci un valore tra login, registrazione e esci\n>");
        String choice = " "; boolean loopDone = false;
        do {
            try {
                choice = keyboard.readLine();
            } catch (IOException e) {exitApplication("lettura user input fallita");} // errore - chiudo applicazione
            if (!choice.equalsIgnoreCase("login") && !choice.equalsIgnoreCase("registrazione") && !choice.equalsIgnoreCase("esci")) { // scelta non valida
                printWarning("Inserisci un valore tra login, registrati e esci. Hai inserito " + choice);
            }
        } while (!choice.equalsIgnoreCase("login") && !choice.equalsIgnoreCase("registrazione") && !choice.equalsIgnoreCase("esci"));
        // scelta valida
        // mando la scelta al server e chiamo le rispettive funzioni
        switch (choice) {
            case "login" -> loopDone = login();
            case "registrazione" -> register();
            default -> { // exit
                out.println("exit");
                return -1; // ritorno -1 per uscire dal gioco
            }
        }
        return loopDone ? 0 : 1; // ritorno 0 se mi sono loggato per passare alla fase del menu
    }

    // MENU
    // loop finché non faccio logout oppure exit
    // chiedo al client l'opzione che vuole scegliere e chiamo le rispettive funzioni
    // se faccio logout ritorno false, non uscire dal gioco - cosi riparte dal login
    // se vuole uscire ritorno true e nel run esce dal loop
    private static boolean menu() {
        boolean exit;
        while (true) {// ripropongo il menu fino al logout o esci
            //avvio del menu - 1 gioca - 2 statistiche - 3 - sharing - 4 logout
            System.out.print("1) Gioca\n2) Statistiche\n3) Sharing\n4) Logout \n5) Esci\n\"Inserisci un valore tra gioca, statistiche, sharing, logout, esci\n>");
            // chiedo all'utente un valore tra 1 e 5 e lo salvo in menuchoice
            // anche qui catturo l'eccezione che potrebbe generare nextInt anche solo digitando una lettera
            String menuchoice;
            do {
                try {
                    menuchoice = keyboard.readLine();
                } catch (IOException e) {printError("lettura user input fallita"); menuchoice = " "; }// esci
                if (!menuchoice.equalsIgnoreCase("gioca") && !menuchoice.equalsIgnoreCase("statistiche") && !menuchoice.equalsIgnoreCase("sharing") &&  !menuchoice.equalsIgnoreCase("logout") && !menuchoice.equalsIgnoreCase("esci"))
                    printWarning("Inserisci un valore tra gioca, statistiche, sharing, logout, esci. Hai inserito " + menuchoice);
            } while (!menuchoice.equalsIgnoreCase("gioca") && !menuchoice.equalsIgnoreCase("statistiche") && !menuchoice.equalsIgnoreCase("sharing") &&  !menuchoice.equalsIgnoreCase("logout") && !menuchoice.equalsIgnoreCase("esci"));
            // scelta del menu valida
            if (menuchoice.equalsIgnoreCase("gioca")) { //gioca
                if (playWordle()) { // posso giocare
                    while(sendWord()); // finche non l'utente non inserisce EXIT oppure ho finito parole, sessione cambiata eccc
                }
            } else if (menuchoice.equalsIgnoreCase("statistiche")) { // sendMeStatistics
                sendMeStatistics();
            } else if (menuchoice.equalsIgnoreCase("sharing")) {// stampo le notifiche raccolte dal MNC
                showMeSharing();
            } else if (menuchoice.equalsIgnoreCase("logout")) {// logout
                logout();
                exit = false; // logout ma non voglio uscire dal gioco, ritorno alla fase di login
                break;
            } else { // esci
                logout(); // comunque faccio logout (almeno chiude il MNC)
                out.println("exit");
                exit =  true; // user vuole uscire, dico al server che ho finito e chiudo
                break;
            }
        }
        return exit;
    }

    // chiede al server di iniziare una nuova sessione o riprendere la sessione interrotta
    // usata nel menu per sapere se posso iniziare a mandare parole : ritorna 0. Altrimenti errori 1 non loggato e 2 gia giocato
    private static boolean playWordle() {
        try {
            out.println("playWORDLE");
            // leggo messaggio iniziale per sapere se posso giocare
            String err = in.readLine();
            if (err.equals("1")) {
                printWarning("Devi prima fare il login per giocare!\n");
                return false;
            }
            else if (err.equals("2")) {
                printWarning("Hai già giocato la parola corrente, riprova quando sarà cambiata!\n");
                return false;
            }
            // err = 0 - posso giocare, inizio a mandare parole
            System.out.println("Puoi iniziare a mandare parole...(EXIT per uscire)");
            return true;
        } catch (IOException e) {
            exitApplication(" (playWordle) lettura risposta dal server: "+ e.getMessage());
            return false;
        }
    }

    // sendWord() è usato per mandare una parola al server
    // prima di tutto ricevo un messaggio(err) per sapere se posso mandarla,
    // (err=1,2) non posso - il server potrebbe non far piu mandare parole per vittoria o fine tentativi o se e cambiata la sessione nel mezzo della partita
    // (err=0) ok - chiedo all'utente la parola da mandare, la mando al server e in base a se ho vinto, ho finito o mi da i consigli agisco
    private static boolean sendWord() {
        try {
            out.println("sendWord");
            // fase di controllo errori, il server potrebbe non far piu mandare parole
            String err = in.readLine();
            // se la parola segreta è cambiata, o ho finito le parole da mandare ritorno exit
            if (err.equals("1")) {
                printError("La parola segreta è cambiata nel mezzo della tua sessione, prova con quella nuova...\n");
                return false;
            } else if (err.equals("2")) {
                printWarning("Hai finito il le parole da mandare!");
                return false;
            }

            // posso inviare la parola - err = 0
            // chiedo la parola e la invio al server
            System.out.print("Inserire la parola da provare (lunga 10) (exit per uscire): ");
            String word;
            try {
                word = keyboard.readLine();
            } catch (IOException e) {printError("lettura user input fallita"); word = "no";}

            out.println(word);
            // se l'utente vuole uscire dall'invio delle parole
            if (word.equalsIgnoreCase("exit")) return false;
            // risposta del server, indizio oppure parola sbagliata
            // System.out.println("Non hai inserito  EXIT.. risposta del server: ");
            String res = in.readLine();
            // System.out.println(res);
            if (res.equals("noWord")) { // ho inviato una parola non esistente
                printWarning("Hai inserito una parola inesistente: " + word);
                return true;
            }
            else if (res.equals("secretWord")) { // ho indovinato la parola segreta
                System.out.println(in.readLine().replace("$", "\n")); // per prima cosa mi manda gli hints della sw(tutto verde), e li stampo subito
                printSuccess("Hai indovinato la parola segreta!!!");
                // share - dopo la vittoria chiedo all'utente se vuole condividere la partita
                System.out.print("Vuoi condividere la partita appena vinta? \n(inserire si oppure 1 per condividerla)>");
                String condividi;
                try {
                    condividi = keyboard.readLine();
                } catch (IOException e) {printError("lettura user input fallita"); condividi = " ";}

                if (condividi.equals("1") || condividi.equals("si")) {
                    share();
                }
                else out.println("0");
                sendMeStatistics();
                return false;
            }
            else { // parola esistente, non è la secret word, ricevo e stampo gli hints
                System.out.println(in.readLine().replace("$", "\n")); // stampo gli hints
                String wordCount = in.readLine(); // word count, se è 12 stoppo il loop
                if (wordCount.equals("12")) {
                    printWarning("Hai raggiunto il massimo di parole inviabili! Partita finita, riprova la prossima sessione...");
                    sendMeStatistics();
                    return false;
                }
                return true;
            }
        } catch (IOException e) {
            exitApplication(" (sendWord) invio della parola da provare: "+ e.getMessage());
        }
        return false;
    }

    // riceve le statistiche dal server, le formatta e le mostra all'utente
    private static void sendMeStatistics() {
        try {
            out.println("sendMeStatistics");
            System.out.println("\n"+in.readLine().replace("$", "\n"));
        } catch (Exception e) {exitApplication(" (sendMeStatistics) la richiesta delle statistiche: "+ e.getMessage());}
    }

    // condividi la partita appena vinta, chiamata al momento della vittoria
    private static void share() {
        try {
            out.println("share");
            String err = in.readLine();
            if (err.equals("0"))
                printSuccess("Hai condiviso la partita con successo!");
            else printWarning("Non puoi condividere la partita!");
        } catch (IOException e) {
            exitApplication(" (share) durante la condivisione della partita: "+ e.getMessage());
        }
    }

    // MulticastNotifyCollector attivato dopo la login
    // attende le notifiche dagli altri e le salva nella lista notifiche
    private static void startMNC() {
        new MulticastNotifyCollector(notifiche, username, SERVERMULTICAST, PORTAMULTICAST);
    }


    // mostra al client le notifiche che sono arrivate dal momento della login
    private static void showMeSharing() {
        if (notifiche.isEmpty()) {
            System.out.println("Non ci sono notifiche...");
            return;
        }
        System.out.println("Notifiche: ");
        // printo le notifiche
        for (String s: notifiche) {
            System.out.println(s);
        }
        notifiche.clear(); //resetto l'array
    }

    // invia al server la richiesta di registrazione, manda username e password e informa l'utente dell'esito
    private static void register() {
        out.println("register");
        int err = sendUserPass();
        if (err == 1)
            printWarning("Password vuota");
        else if (err == 2)
            printWarning("Username gia esistente");
        else if (err == 3)
            printError("Errore nella scrittura del database, riprova");
        else if (err == 0) { // registrato con successo
            printSuccess("Registrazione avvenuta con successo!");
        }
    }

    // invia al server la richiesta di login, manda username e password e informa l'utente dell'esito
    private static boolean login() {
        out.println("login");
        int err = sendUserPass();
        if (err == 1)
            printWarning("Username non trovato");
        else if (err == 2)
            printWarning("Password errata");
        else if (err == 3)
            printWarning("Sei gia loggato");
        else if (err == 0){ // err = 0, mi sono loggato, aggiorno lo username
            try {
                username = in.readLine();
                printSuccess("Login avvenuta con successo!");
                return true;
            } catch (IOException e) {
                exitApplication(" (login success) lettura username dal server: " + e.getMessage());
            }
        }
        return false;
    }



    private static int sendUserPass() {
        // chiedo all'utente username e password -
        // controllo l'input: entrambi non devono essere >=12 e
        // username non deve contenere il pattern &%#$ che uso per disattivare gli ascoltatori multicast di specifici client
        // ritorna la risposta del server in base a se ha chiesto di fare login o registrazione
        try {
            String username;
            do {
                System.out.print("Inserire username: ");
                username = keyboard.readLine();
                if (username.contains(" ")) printWarning("username non puó contenere spazi");
                if (username.length() >= 12) printWarning("lunghezza massima username è 12");
                if (username.contains("&%#$")) printWarning("&%#$ pattern non consentito negli username");
            }
            while (username.contains(" ") || username.length() >= 12 || username.contains("&%#$"));
            String password;
            do {
                System.out.print("Inserire password: ");
                password = keyboard.readLine();
                if (username.contains(" ")) printWarning("username non puó contenere spazi");
                if (password.length() >= 12) printWarning("lunghezza massima password è 12");
            }
            while (username.contains(" ") || password.length() >= 12);
            // li invio al server
            out.println(username);
            out.println(password);
            // aspetto la risposta dal server
            return Integer.parseInt(in.readLine());
        } // errore nella lettura sul socket
        catch (IOException e) { // return false così il programma finisce
            exitApplication(" (sendUserPass) lettura input fallita:" +   e.getMessage());
        }
        return -1;
    }

    private static void logout() {
        out.println("logout");
        printSuccess("Hai eseguito il logout dal servizio, " + username);
        username = null;
    }

    // funzioni per printare messaggi particolari: warning, errore o successo
    private static void printWarning(String msg) {
        System.out.println(YELLOW_BACKGROUND + "Attenzione: " + ANSI_END + " " + msg);
    }

    private static void printError(String msg) {
        System.out.println(RED_BACKGROUND + "Errore: " + ANSI_END + " " + msg);
    }

    private static void printSuccess(String msg) {
        System.out.println(GREEN_BACKGROUND + msg + ANSI_END);
    }

    public static void readConfig() {
        try {
            InputStream input = new FileInputStream(configFile);
            Properties prop = new Properties();
            prop.load(input);
            SERVER = prop.getProperty("server");
            PORTASERVER = Integer.parseInt(prop.getProperty("portaserver"));
            SERVERMULTICAST = prop.getProperty("servermulticast");
            PORTAMULTICAST = Integer.parseInt(prop.getProperty("portamulticast"));
            input.close();
        } catch (IOException e) {
            e.printStackTrace();
            exitApplication("Errore nella lettura del file di configurazione");
        }
    }

    // usata quando cè un errore di I/O, printo un messaggio customizzato e esco dal programma
    private static void exitApplication(String msg) {
        printError(msg+", chiusura dell'applicazione... ");
        System.exit(1);
    }
}