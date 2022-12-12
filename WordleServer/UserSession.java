public class UserSession {
    public static final int MAX_TRIES = 12; // massimo numero di prove di parole che posso fare
    private int wordCount; //numero di parole provate
    private final StringBuilder hints; //stringa degli hints delle parole mandate fino ad adesso
    private final StringBuilder hintsBG; // come sopra ma con gli _ al posto delle parole, usata per condividere la parita
    private boolean shared; // se ho condiviso la partita
    private boolean win; // se ho vinto
    private final String username; // lo username dell'user di questa sessione


    public UserSession(String username) {
        this.username = username;
        this.wordCount = 0;
        this.hints = new StringBuilder();
        this.hintsBG = new StringBuilder();
        this.shared = false;
        this.win = false;
    }

    // se posso condividere, costruisce la notifica e la ritorna
    // prende come argomento wordDurationDate che UserSession non conosce
    public String getWinNotification(String wordDurationDate) {
        if (!this.win || this.shared)
            return "";
        else return this.username + ", ha vinto la parola del " + wordDurationDate +
                " in " + getWordCount() +" tentativi su 12.\n"+this.hintsBG;
    }

    public int getWordCount() {
        return wordCount;
    }

    public void addWordCount() {
        this.wordCount ++;
    }

    // in next hint aggiorno gli hints
    public String nextHint(String hints) {
        this.hints.append(hints).append("$");
        return this.hints.toString(); // ritorno solo il valore
    }

    // aggiorno gli hints senza le lettere, solo background e _ per le notifiche
    public void nexHintBG(String hintsbg) {
        this.hintsBG.append(hintsbg).append("\n");
    }

    public boolean isShared() {
        return shared;
    }

    public boolean hasFinished() {
        return this.win || this.wordCount >= MAX_TRIES;
    }

    // una volta condivisa la partita oppure vinta non si puó piú cambiare
    public void setShared() {
        this.shared = true;
    }
    public void setWinner(boolean win) {
        this.win = win;
    }
    public boolean isWinner() {
        return this.win;
    }



}

