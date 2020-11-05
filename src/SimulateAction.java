public class SimulateAction extends TablutAction{

    private double prob;

    public SimulateAction(Coordinates coordinates, Pawn pawn, double prob) {
        super(coordinates, pawn);
        this.prob = prob;
    }

    public SimulateAction(TablutAction action, double prob) {
        super(action.coordinates, action.pawn);
        this.prob = prob;
    }

    public double getProb() {
        return prob;
    }

    public void setProb(double prob) {
        this.prob = prob;
    }
    
}
