package pt.bamer.bamerosbuffer.helpers;

import java.util.Timer;

import pt.bamer.bamerosbuffer.PainelGlobal;

public class TimerDePainelGlobal extends Timer {
    public TimerDePainelGlobal(PainelGlobal activity) {
        activity.addTimer(this);
    }
}
