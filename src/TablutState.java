import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Function;

public class TablutState {
    public static final byte EMPTY = 0;
    public static final byte WHITE = 1;
    public static final byte BLACK = 2;
    public static final byte KING = 3;
    public static final byte ESCAPE = 1;
    public static final byte CAMP = 2;
    public static final byte CITADEL = 3;

    public static final int BOARD_SIZE = 9;

    public static final int WHITE_PAWNS = 8;
    public static final int BLACK_PAWNS = 16;

    private static final byte[][] board = initBoard();

    private byte[][] pawns;
    private byte playerTurn;
    private Coordinates kingPosition;

    private boolean whiteWin = false;
    private boolean blackWin = false;
    private boolean draw = false;
    private int blackPawns;
    private int whitePawns;
    private LinkedList<TablutState> drawConditions;
    private HashMap<Coordinates, ArrayList<LinkedList<TablutAction>>> actionsMap;
    private HashMap<Capture, LinkedList<Coordinates>> capturesMap;

    private int newActiveCaptures;
    private boolean firstMove = false;
    private TablutAction firstAction = null;
    private TablutAction previousAction = null;

    public enum Weights {
        TOTAL_DIFF(0), ACTIVE_CAPTURES(1), KING_MOVES_DIFF(2), WILL_BE_CAPTURED(3), KING_CHECKMATE(4);

        private int value;

        private Weights(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }

    private enum Directions {
        UP(0), RIGHT(1), DOWN(2), LEFT(3);

        private int value;

        private Directions(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }

    public TablutState(byte playerTurn) {
        this.playerTurn = playerTurn;
        this.kingPosition = new Coordinates(4, 4);
        this.whitePawns = WHITE_PAWNS;
        this.blackPawns = BLACK_PAWNS;
        this.firstMove = true;
        this.drawConditions = new LinkedList<>();
        this.drawConditions.add(this);
        initPawns();
        initActionMap();
    }

    public TablutState(byte playerTurn, byte[][] pawns) {
        this.pawns = pawns;
        this.playerTurn = playerTurn;
        this.kingPosition = getKingPosition();
        this.whitePawns = WHITE_PAWNS;
        this.blackPawns = BLACK_PAWNS;
        this.firstMove = false;
        this.drawConditions = new LinkedList<>();
        this.drawConditions.add(this);
        initActionMap();
    }

    public TablutState(byte[][] pawns, byte playerTurn, boolean firstMove, TablutAction firstAction, LinkedList<TablutState> drawConditions) {
        this.pawns = pawns;
        this.playerTurn = playerTurn;
        this.blackPawns = 0;
        this.whitePawns = 0;
        this.drawConditions = new LinkedList<>();
        this.drawConditions.addAll(drawConditions);
        this.drawConditions.add(this);
        this.firstMove = firstMove;
        this.firstAction = firstAction;
        initState();
        initActionMap();
    }

    private TablutState(TablutState state, byte[][] pawns, Coordinates kingPosition,
            LinkedList<TablutState> drawConditions,
            HashMap<Coordinates, ArrayList<LinkedList<TablutAction>>> actionsMap) {
        this.pawns = pawns;
        this.playerTurn = state.getPlayerTurn();
        this.blackPawns = state.getBlackPawns();
        this.whitePawns = state.getWhitePawns();
        this.draw = state.isDraw();
        this.kingPosition = kingPosition;
        this.blackWin = state.isBlackWin();
        this.whiteWin = state.isWhiteWin();
        this.drawConditions = new LinkedList<>();
        this.drawConditions.addAll(drawConditions);
        this.firstMove = state.isFirstMove();
        this.firstAction = state.getFirstAction();
        this.previousAction = state.getPreviousAction();
        this.actionsMap = actionsMap;
    }

    private Coordinates getKingPosition() {
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++)
                if (pawns[i][j] == KING)
                    return new Coordinates(i, j);
        return null;
    }

    public static byte[][] initBoard() {
        byte[][] board = new byte[BOARD_SIZE][BOARD_SIZE];
        for (int i = 1; i < BOARD_SIZE - 1; i++) {
            if (i < 3 || i > 5) {
                board[0][i] = ESCAPE;
                board[i][0] = ESCAPE;
                board[BOARD_SIZE - 1][i] = ESCAPE;
                board[i][BOARD_SIZE - 1] = ESCAPE;
            } else {
                board[0][i] = CAMP;
                board[i][0] = CAMP;
                board[BOARD_SIZE - 1][i] = CAMP;
                board[i][BOARD_SIZE - 1] = CAMP;
            }
        }
        board[1][4] = CAMP;
        board[4][1] = CAMP;
        board[BOARD_SIZE - 2][4] = CAMP;
        board[4][BOARD_SIZE - 2] = CAMP;
        board[4][4] = CITADEL;
        return board;
    }

    private void initState() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (pawns[i][j] == BLACK)
                    blackPawns++;
                else if (pawns[i][j] == WHITE)
                    whitePawns++;
                else if (pawns[i][j] == KING) {
                    whitePawns++;
                    kingPosition = new Coordinates(i, j);
                }
            }
        }
        if (kingPosition == null)
            blackWin = true;
        else if (isOnPosition(kingPosition, ESCAPE))
            whiteWin = true;
    }

    public byte[][] getBoard() {
        return pawns;
    }

    private void initActionMap() {
        this.capturesMap = new HashMap<>();
        this.actionsMap = new HashMap<>();
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (pawns[i][j] != EMPTY) {
                    Coordinates coord = new Coordinates(i, j);
                    this.actionsMap.put(coord, getPawnActions(coord, pawns[i][j]));
                }
            }
    }

    // TODO - change .add to .set in some way -> .add is not constant time!!!
    private ArrayList<LinkedList<TablutAction>> getPawnActions(Coordinates coord, byte pawn) {
        ArrayList<LinkedList<TablutAction>> actionArray = new ArrayList<>(4);
        actionArray.add(Directions.UP.value(), searchActions(coord, pawn, -1, true));
        actionArray.add(Directions.RIGHT.value(), searchActions(coord, pawn, 1, false));
        actionArray.add(Directions.DOWN.value(), searchActions(coord, pawn, 1, true));
        actionArray.add(Directions.LEFT.value(), searchActions(coord, pawn, -1, false));
        return actionArray;
    }

    public void initPawns() {
        pawns = new byte[BOARD_SIZE][BOARD_SIZE];
        for (int i = 1; i < BOARD_SIZE - 1; i++) {
            if (i < 3) {
                pawns[4][i + 1] = WHITE;
                pawns[i + 1][4] = WHITE;
                pawns[4][BOARD_SIZE - i - 2] = WHITE;
                pawns[BOARD_SIZE - i - 2][4] = WHITE;
            } else if (i <= 5) {
                pawns[0][i] = BLACK;
                pawns[i][0] = BLACK;
                pawns[BOARD_SIZE - 1][i] = BLACK;
                pawns[i][BOARD_SIZE - 1] = BLACK;
            }
        }
        pawns[1][4] = BLACK;
        pawns[4][1] = BLACK;
        pawns[BOARD_SIZE - 2][4] = BLACK;
        pawns[4][BOARD_SIZE - 2] = BLACK;
        pawns[4][4] = KING;
    }

    @Override
    public String toString() {
        String result = "";
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (pawns[i][j] == BLACK)
                    result = result + "B";
                else if (pawns[i][j] == WHITE)
                    result = result + "W";
                else if (pawns[i][j] == KING)
                    result = result + "K";
                else if (pawns[i][j] == EMPTY)
                    if(board[i][j] == CITADEL) 
                        result = result + "T";
                    else if(board[i][j] == CAMP)
                        result = result + "X";
                    else
                        result = result + "-";
                else
                    result = result + "?";
            }
            result = result + "\n";
        }
        result = result + "\n";
        return result;
    }

    @Override
    public TablutState clone() {
        byte[][] newPawns = new byte[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++)
                newPawns[i][j] = pawns[i][j];
        HashMap<Coordinates, ArrayList<LinkedList<TablutAction>>> newActionsMap = new HashMap<>();

        for (Map.Entry<Coordinates, ArrayList<LinkedList<TablutAction>>> entry : this.actionsMap.entrySet()) {
            ArrayList<LinkedList<TablutAction>> aList = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                LinkedList<TablutAction> list = new LinkedList<>();
                for (TablutAction a : entry.getValue().get(i))
                    list.add(a.copy());
                aList.add(i, list);
            }
            newActionsMap.put(entry.getKey(), aList);
        }

        TablutState newState = new TablutState(this, newPawns, kingPosition, drawConditions, newActionsMap);
        return newState;
    }

    public TablutState copySimulation() {
        byte[][] newPawns = new byte[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++)
                newPawns[i][j] = pawns[i][j];
        TablutState newState = new TablutState(this, newPawns, kingPosition, drawConditions, actionsMap);
        return newState;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (this.getClass() != obj.getClass())
            return false;
        TablutState state = (TablutState) obj;
        for (int i = 0; i < BOARD_SIZE; i++)
            for (int j = 0; j < BOARD_SIZE; j++)
                if (pawns[i][j] != state.getPawns()[i][j])
                    return false;
        return true;
    }

    public void printList(LinkedList<TablutAction> l) {
        for (TablutAction a : l)
            System.out.println(a.toString());
    }

    private void updateActionMap(TablutAction action, boolean temp) {
        if (temp) {
            captureMapCopy.remove(new Capture(action.pawn));
            ArrayList<LinkedList<TablutAction>> previousActions = actionsMap.get(action.pawn.position);
            for (LinkedList<TablutAction> list : previousActions) {
                for (TablutAction a : list) {
                    for (Capture c : a.getCaptured()) {
                        updateCaptureMap(captureMapCopy, c, a.pawn.position, false);
                    }
                }
            }
            for (Capture c : action.getCaptured()) {
                newActiveCaptures++;
                captureMapCopy.remove(c);
                for (LinkedList<TablutAction> list : actionsMap.get(c.getCaptured().position)) {
                    for (TablutAction a : list) {
                        for (Capture c1 : a.getCaptured()) {
                            updateCaptureMap(captureMapCopy, c1, a.pawn.position, false);
                        }
                    }
                }
                Directions d = getDirection(action.coordinates, c.getCaptured().position);
                actionsTowardsCoordinates(c.getCaptured().position, getOtherAxisDirections(d), temp);
                capturesTowardsCoordinates(c.getCaptured().position, c.getCaptured().getPawnType(), temp);
                removeCapturesAssumingCoordinatesBlocker(c.getCaptured().position, c.getCaptured().getPawnType(),
                        oppositeDirection(d), temp);
            }
            countCaptures(action.coordinates, action.pawn.getPawnType(), Directions.UP);
            countCaptures(action.coordinates, action.pawn.getPawnType(), Directions.RIGHT);
            countCaptures(action.coordinates, action.pawn.getPawnType(), Directions.DOWN);
            countCaptures(action.coordinates, action.pawn.getPawnType(), Directions.LEFT);
        } else {
            this.actionsMap.remove(action.pawn.position);
            for (Capture c : action.getCaptured()) {
                this.actionsMap.remove(c.getCaptured().position);
                Directions d = getDirection(action.coordinates, c.getCaptured().position);
                actionsTowardsCoordinates(c.getCaptured().position, getOtherAxisDirections(d), temp);
                capturesTowardsCoordinates(c.getCaptured().position, c.getCaptured().getPawnType(), temp);
                removeCapturesAssumingCoordinatesBlocker(c.getCaptured().position, c.getCaptured().getPawnType(),
                        oppositeDirection(d), temp);
            }
            ArrayList<LinkedList<TablutAction>> list = new ArrayList<>(4);
            list.add(Directions.UP.value(), searchActions(action.coordinates, action.pawn.getPawnType(), -1, true));
            list.add(Directions.RIGHT.value(), searchActions(action.coordinates, action.pawn.getPawnType(), 1, false));
            list.add(Directions.DOWN.value(), searchActions(action.coordinates, action.pawn.getPawnType(), 1, true));
            list.add(Directions.LEFT.value(), searchActions(action.coordinates, action.pawn.getPawnType(), -1, false));
            this.actionsMap.remove(action.coordinates);
            this.actionsMap.put(action.coordinates, list);
        }

        actionsTowardsCoordinates(action.coordinates, Directions.values(), temp);
        capturesTowardsCoordinates(action.coordinates, getValue(pawns, action.coordinates), temp);

        Directions direction = getDirection(action.pawn.position, action.coordinates);
        actionsTowardsCoordinatesNotOnDirection(action.pawn.position, direction, temp);
        capturesTowardsCoordinates(action.pawn.position, getValue(pawns, action.coordinates), temp);

        capturesAssumingCoordinatesBlocker(action.coordinates, temp);
        removeCapturesAssumingCoordinatesBlocker(action.pawn.position, getValue(pawns, action.coordinates), null, temp);
    }

    public void updateCaptureMap(HashMap<Capture, LinkedList<Coordinates>> capMap, Capture cap, Coordinates attacker,
            boolean add) {
        LinkedList<Coordinates> attackerList = capMap.get(cap);
        if (attackerList == null) {
            attackerList = new LinkedList<>();
            capMap.put(cap, attackerList);
        }
        if (add)
            attackerList.add(attacker);
        else
            attackerList.remove(attacker);
        if (attackerList.isEmpty())
            capMap.remove(cap);
    }

    private Directions[] getOtherAxisDirections(Directions dir) {
        if (dir == Directions.DOWN || dir == Directions.UP)
            return new Directions[] { Directions.RIGHT, Directions.LEFT };
        else
            return new Directions[] { Directions.UP, Directions.DOWN };
    }

    private void capturesAssumingCoordinatesBlocker(Coordinates coordinates, boolean temp) {
        int row = coordinates.row;
        int column = coordinates.column;
        if ((row == 0 && column == 4) || (row == 4 && column == 0) || (row == BOARD_SIZE - 1 && column == 4)
                || (row == 4 && column == BOARD_SIZE - 1)) {
            return;
        }
        Function<Coordinates, Boolean> condition = (Coordinates c) -> {
            return isEnemy(getValue(pawns, c), getValue(pawns, coordinates) == BLACK ? WHITE : BLACK);
        }; // ??
        Coordinates result;
        if (isBoardBlocker(coordinates))
            return;
        if (row + 2 < BOARD_SIZE && isEnemy(pawns[row + 1][column], getValue(pawns, coordinates))
                && pawns[row + 2][column] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row + 2, column);
            Coordinates captured = new Coordinates(row + 1, column);
            result = searchByDirection(emptyPos, Directions.RIGHT, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.RIGHT, getValue(pawns, captured), temp);
            result = searchByDirection(emptyPos, Directions.LEFT, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.LEFT, getValue(pawns, captured), temp);
            result = searchByDirection(emptyPos, Directions.DOWN, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.DOWN, getValue(pawns, captured), temp);
        }
        if (row - 2 >= 0 && isEnemy(pawns[row - 1][column], getValue(pawns, coordinates))
                && pawns[row - 2][column] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row - 2, column);
            Coordinates captured = new Coordinates(row - 1, column);
            result = searchByDirection(emptyPos, Directions.RIGHT, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.RIGHT, getValue(pawns, captured), temp);
            result = searchByDirection(emptyPos, Directions.LEFT, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.LEFT, getValue(pawns, captured), temp);
            result = searchByDirection(emptyPos, Directions.UP, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.UP, getValue(pawns, captured), temp);
        }
        if (column + 2 < BOARD_SIZE && isEnemy(pawns[row][column + 1], getValue(pawns, coordinates))
                && pawns[row][column + 2] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row, column + 2);
            Coordinates captured = new Coordinates(row, column + 1);
            result = searchByDirection(emptyPos, Directions.UP, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.UP, getValue(pawns, captured), temp);
            result = searchByDirection(emptyPos, Directions.DOWN, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.DOWN, getValue(pawns, captured), temp);
            result = searchByDirection(emptyPos, Directions.RIGHT, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.RIGHT, getValue(pawns, captured), temp);
        }
        if (column - 2 >= 0 && isEnemy(pawns[row][column - 1], getValue(pawns, coordinates))
                && pawns[row][column - 2] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row, column - 2);
            Coordinates captured = new Coordinates(row, column - 1);
            result = searchByDirection(emptyPos, Directions.UP, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.UP, getValue(pawns, captured), temp);
            result = searchByDirection(emptyPos, Directions.DOWN, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.DOWN, getValue(pawns, captured), temp);
            result = searchByDirection(emptyPos, Directions.LEFT, condition);
            if (result != null)
                updateCaptures(captured, result, emptyPos, Directions.LEFT, getValue(pawns, captured), temp);
        }
    }

    private void removeCapturesAssumingCoordinatesBlocker(Coordinates coordinates, byte oldPawn, Directions direction,
            boolean temp) {
        int row = coordinates.row;
        int column = coordinates.column;

        if ((row == 0 && column == 4) || (row == 4 && column == 0) || (row == BOARD_SIZE - 1 && column == 4)
                || (row == 4 && column == BOARD_SIZE - 1)) {
            return;
        }
        Function<Coordinates, Boolean> condition = (Coordinates c) -> {
            return isEnemy(getValue(pawns, c), oldPawn == BLACK ? WHITE : BLACK);
        }; // ??
        if (isBoardBlocker(coordinates))
            return;
        Coordinates result;
        if (direction != Directions.DOWN && row + 2 < BOARD_SIZE && isEnemy(pawns[row + 1][column], oldPawn)
                && pawns[row + 2][column] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row + 2, column);
            Coordinates captured = new Coordinates(row + 1, column);
            result = searchByDirection(emptyPos, Directions.RIGHT, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.RIGHT, temp);
            result = searchByDirection(emptyPos, Directions.LEFT, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.LEFT, temp);
            result = searchByDirection(emptyPos, Directions.DOWN, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.DOWN, temp);
        }
        if (direction != Directions.UP && row - 2 >= 0 && isEnemy(pawns[row - 1][column], oldPawn)
                && pawns[row - 2][column] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row - 2, column);
            Coordinates captured = new Coordinates(row - 1, column);
            result = searchByDirection(emptyPos, Directions.RIGHT, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.RIGHT, temp);
            result = searchByDirection(emptyPos, Directions.LEFT, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.LEFT, temp);
            result = searchByDirection(emptyPos, Directions.UP, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.UP, temp);
        }
        if (direction != Directions.RIGHT && column + 2 < BOARD_SIZE && isEnemy(pawns[row][column + 1], oldPawn)
                && pawns[row][column + 2] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row, column + 2);
            Coordinates captured = new Coordinates(row, column + 1);
            result = searchByDirection(emptyPos, Directions.UP, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.UP, temp);
            result = searchByDirection(emptyPos, Directions.DOWN, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.DOWN, temp);
            result = searchByDirection(emptyPos, Directions.RIGHT, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.RIGHT, temp);
        }
        if (direction != Directions.LEFT && column - 2 >= 0 && isEnemy(pawns[row][column - 1], oldPawn)
                && pawns[row][column - 2] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row, column - 2);
            Coordinates captured = new Coordinates(row, column - 1);
            result = searchByDirection(emptyPos, Directions.UP, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.UP, temp);
            result = searchByDirection(emptyPos, Directions.DOWN, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.DOWN, temp);
            result = searchByDirection(emptyPos, Directions.LEFT, condition);
            if (result != null)
                removeCaptures(captured, result, emptyPos, Directions.LEFT, temp);
        }
    }

    private void actionsTowardsCoordinatesNotOnDirection(Coordinates position, Directions direction, boolean temp) {
        Function<Coordinates, Boolean> condition = (Coordinates c) -> {
            return getValue(pawns, c) != EMPTY;
        };
        Coordinates result;
        if (direction == Directions.UP || direction == Directions.DOWN) {
            result = searchByDirection(position, Directions.LEFT, condition);
            if (result != null)
                updateActionsByCoordinates(result, Directions.LEFT, position, temp);
            result = searchByDirection(position, Directions.RIGHT, condition);
            if (result != null)
                updateActionsByCoordinates(result, Directions.RIGHT, position, temp);
        } else {
            result = searchByDirection(position, Directions.UP, condition);
            if (result != null)
                updateActionsByCoordinates(result, Directions.UP, position, temp);
            result = searchByDirection(position, Directions.DOWN, condition);
            if (result != null)
                updateActionsByCoordinates(result, Directions.DOWN, position, temp);
        }
    }

    private void actionsTowardsCoordinates(Coordinates coordinates, Directions[] directions, boolean temp) {
        Function<Coordinates, Boolean> condition = (Coordinates c) -> {
            return getValue(pawns, c) != EMPTY;
        };
        Coordinates result;
        for (Directions dir : directions) {
            result = searchByDirection(coordinates, dir, condition);
            if (result != null)
                updateActionsByCoordinates(result, dir, coordinates, temp);
        }
    }

    private Directions getDirection(Coordinates prev, Coordinates next) {
        if (prev.row == next.row) {
            if (prev.column > next.column)
                return Directions.LEFT;
            else
                return Directions.RIGHT;
        } else {
            if (prev.row > next.row)
                return Directions.UP;
            else
                return Directions.DOWN;
        }
    }

    private void capturesTowardsCoordinates(Coordinates coordinates, byte captured, boolean temp) {
        int row = coordinates.row;
        int column = coordinates.column;
        if ((row == 0 && column == 4) || (row == 4 && column == 0) || (row == BOARD_SIZE - 1 && column == 4)
                || (row == 4 && column == BOARD_SIZE - 1)) {
            return;
        }
        byte enemy = captured == BLACK ? WHITE : BLACK;
        Coordinates result;
        Function<Coordinates, Boolean> condition = (Coordinates c) -> {
            return isEnemy(getValue(pawns, c), captured);
        }; // ??
        if (column - 1 >= 0 && isBlocker(row, column - 1, enemy) && column + 1 < BOARD_SIZE
                && pawns[row][column + 1] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row, column + 1);
            result = searchByDirection(emptyPos, Directions.UP, condition);
            if (result != null)
                updateCaptures(coordinates, result, emptyPos, Directions.UP, captured, temp);
            result = searchByDirection(emptyPos, Directions.DOWN, condition);
            if (result != null)
                updateCaptures(coordinates, result, emptyPos, Directions.DOWN, captured, temp);
        }
        if (column + 1 < BOARD_SIZE && isBlocker(row, column + 1, enemy) && column - 1 >= 0
                && pawns[row][column - 1] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row, column - 1);
            result = searchByDirection(emptyPos, Directions.UP, condition);
            if (result != null)
                updateCaptures(coordinates, result, emptyPos, Directions.UP, captured, temp);
            result = searchByDirection(emptyPos, Directions.DOWN, condition);
            if (result != null)
                updateCaptures(coordinates, result, emptyPos, Directions.DOWN, captured, temp);
        }
        if (row - 1 >= 0 && isBlocker(row - 1, column, enemy) && row + 1 < BOARD_SIZE
                && pawns[row + 1][column] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row + 1, column);
            result = searchByDirection(emptyPos, Directions.LEFT, condition);
            if (result != null)
                updateCaptures(coordinates, result, emptyPos, Directions.LEFT, captured, temp);
            result = searchByDirection(emptyPos, Directions.RIGHT, condition);
            if (result != null)
                updateCaptures(coordinates, result, emptyPos, Directions.RIGHT, captured, temp);
        }
        if (row + 1 < BOARD_SIZE && isBlocker(row + 1, column, enemy) && row - 1 >= 0
                && pawns[row - 1][column] == EMPTY) {
            Coordinates emptyPos = new Coordinates(row - 1, column);
            result = searchByDirection(emptyPos, Directions.LEFT, condition);
            if (result != null)
                updateCaptures(coordinates, result, emptyPos, Directions.LEFT, captured, temp);
            result = searchByDirection(emptyPos, Directions.RIGHT, condition);
            if (result != null)
                updateCaptures(coordinates, result, emptyPos, Directions.RIGHT, captured, temp);
        }
    }

    private void updateCaptures(Coordinates capturedPos, Coordinates enemyPos, Coordinates emptyPos,
            Directions direction, byte captured, boolean temp) {
        if (temp) {
            Capture c = new Capture(new Pawn(getValue(pawns, capturedPos), capturedPos));
            if (c.getCaptured().getPawnType() != EMPTY && !isCapture(c, getValue(pawns, enemyPos), emptyPos, enemyPos))
                return;

            if (c.getCaptured().getPawnType() == EMPTY) {
                if (!(captured == KING && !kingCaptured(capturedPos, emptyPos))) {
                    updateCaptureMap(captureMapCopy, c, enemyPos, false);
                }
            } else {
                updateCaptureMap(captureMapCopy, c, enemyPos, true);
            }
        } else {
            TablutAction tempAction = new TablutAction(new Coordinates(emptyPos.row, emptyPos.column),
                    new Pawn(getValue(pawns, enemyPos), enemyPos));
            Capture c = new Capture(new Pawn(getValue(pawns, capturedPos), capturedPos));
            if (c.getCaptured().getPawnType() != EMPTY && !isCapture(c, getValue(pawns, enemyPos), emptyPos, enemyPos))
                return;
            ArrayList<LinkedList<TablutAction>> actionContainer = this.actionsMap.get(enemyPos);
            if (actionContainer == null) {
                actionContainer = new ArrayList<>(4);
                for (int j = 0; j < 4; j++)
                    actionContainer.add(j, new LinkedList<>());
            }

            for (TablutAction a : actionContainer.get(oppositeDirection(direction).value())) {
                if (a.equals(tempAction)) {
                    if (c.getCaptured().getPawnType() == EMPTY) {
                        if (!(captured == KING && !kingCaptured(capturedPos, emptyPos))) {
                            a.removeCapture(c);
                        }
                    } else {
                        a.addCapture(c);
                    }
                    break;
                }
            }
            this.actionsMap.remove(enemyPos);
            this.actionsMap.put(enemyPos, actionContainer);
        }
    }

    private void removeCaptures(Coordinates captured, Coordinates enemyPos, Coordinates emptyPos, Directions direction,
            boolean temp) {
        if (temp) {
            Capture c = new Capture(new Pawn(getValue(pawns, captured), captured));
            if (!isCapture(c, getValue(pawns, enemyPos), emptyPos, enemyPos))
                return;
            updateCaptureMap(captureMapCopy, c, enemyPos, false);
        } else {
            TablutAction tempAction = new TablutAction(new Coordinates(emptyPos.row, emptyPos.column),
                    new Pawn(getValue(pawns, enemyPos), enemyPos));
            Capture c = new Capture(new Pawn(getValue(pawns, captured), captured));
            if (!isCapture(c, getValue(pawns, enemyPos), emptyPos, enemyPos))
                return;
            ArrayList<LinkedList<TablutAction>> actionContainer = this.actionsMap.get(enemyPos);
            if (actionContainer == null) {
                actionContainer = new ArrayList<>(4);
                for (int j = 0; j < 4; j++)
                    actionContainer.add(j, new LinkedList<>());
            }
            for (TablutAction a : actionContainer.get(oppositeDirection(direction).value())) {
                if (a.equals(tempAction)) {
                    a.removeCapture(c);
                    break;
                }
            }
            this.actionsMap.remove(enemyPos);
            this.actionsMap.put(enemyPos, actionContainer);
        }
    }

    private Coordinates searchByDirection(Coordinates pos, Directions direction,
            Function<Coordinates, Boolean> condition) {
        int row = pos.row;
        int column = pos.column;
        Coordinates coord = new Coordinates(row, column);
        boolean camp = false, citadel = false;
        int step = direction == Directions.UP || direction == Directions.LEFT ? -1 : 1;
        for (int i = (direction == Directions.UP || direction == Directions.DOWN ? row + step : column + step); step > 0
                ? i < BOARD_SIZE
                : i >= 0; i += step) {
            if (direction == Directions.UP || direction == Directions.DOWN)
                coord.row = i;
            else
                coord.column = i;
            if (getValue(board, coord) == CAMP)
                camp = true;
            else if (getValue(board, coord) == CITADEL)
                citadel = true;
            else if ((getValue(board, coord) == EMPTY || getValue(board, coord) == ESCAPE) && (camp || citadel))
                break;
            if (condition.apply(coord)) {
                return coord;
            }
            if (getValue(pawns, coord) != EMPTY) {
                break;
            }
        }
        return null;
    }

    private void updateActionsByCoordinates(Coordinates toUpdate, Directions direction, Coordinates towards,
            boolean temp) {
        int step = direction == Directions.UP || direction == Directions.LEFT ? 1 : -1;
        if (temp) {
            countCaptures(toUpdate, getValue(pawns, toUpdate), oppositeDirection(direction));
        } else {
            ArrayList<LinkedList<TablutAction>> actionContainer = this.actionsMap.get(toUpdate);
            if (actionContainer == null) {
                actionContainer = new ArrayList<>(4);
                for (int j = 0; j < 4; j++)
                    actionContainer.add(j, new LinkedList<>());
            }
            actionContainer.set(oppositeDirection(direction).value(), searchActions(toUpdate, getValue(pawns, toUpdate),
                    step, direction == Directions.UP || direction == Directions.DOWN ? true : false));
            this.actionsMap.remove(toUpdate);
            this.actionsMap.put(toUpdate, actionContainer);
        }
    }

    private Directions oppositeDirection(Directions direction) {
        if (direction == Directions.UP)
            return Directions.DOWN;
        if (direction == Directions.DOWN)
            return Directions.UP;
        if (direction == Directions.LEFT)
            return Directions.RIGHT;
        return Directions.LEFT;
    }

    public byte getValue(byte[][] b, Coordinates c) {
        return b[c.row][c.column];
    }

    private boolean isCapture(Capture captured, byte playerCapturing, Coordinates emptyPos, Coordinates enemyPos) {
        if (!isLegal(new TablutAction(emptyPos, new Pawn(playerCapturing, enemyPos))))
            return false;
        if (!isEnemy(pawns[captured.getCaptured().position.row][captured.getCaptured().position.column],
                playerCapturing))
            return false;
        if (pawns[captured.getCaptured().position.row][captured.getCaptured().position.column] == KING
                && !kingCaptured(captured.getCaptured().position, emptyPos))
            return false;
        return true;
    }

    public LinkedList<SimulateAction> getSimulatingActions() {
        if (whiteWin || blackWin || draw)
            return new LinkedList<>();
        LinkedList<TablutAction> actions = getLegalActions();
        LinkedList<SimulateAction> result = new LinkedList<>();
        boolean loosing = false;
        boolean stop = false;
        TablutAction kingAction = null;
        if (actions.isEmpty()) {
            if (playerTurn == WHITE)
                blackWin = true;
            else
                whiteWin = true;
            return result;
        }
        if (playerTurn == BLACK) {
            for (LinkedList<TablutAction> list : actionsMap.get(kingPosition)) {
                for (TablutAction ka : list) {
                    if (isOnPosition(ka.coordinates, ESCAPE)) {
                        kingAction = ka;
                        stop = true;
                        break;
                    }
                }
                if (stop)
                    break;
            }
        }
        if (firstMove) {
            if (playerTurn == WHITE)
                result.add(new SimulateAction(whiteOpening(), 1));
            else {
                result.add(new SimulateAction(blackOpening(firstAction), 1));
                firstMove = false;
            }
            return result;
        }
        for (TablutAction action : actions) {
            if (isWin(action)) {
                result = new LinkedList<>();
                result.add(new SimulateAction(action, 1));
                break;
            } else if (isPreventingLoose(action, kingAction)) {
                if (!loosing)
                    result = new LinkedList<>();
                result.add(new SimulateAction(action, 1));
                loosing = true;
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        //TEST
        double captureProb = 0.75;
        double standardAction = 0.01;
        for(TablutAction action : actions) {
            if(!action.getCaptured().isEmpty()) {
                for(Capture c : action.getCaptured()) {
                    if(c.getCaptured().position.equals(previousAction.coordinates)) {
                        result = new LinkedList<>();
                        result.add(new SimulateAction(action, captureProb));
                        return result;
                    }
                }
                result.add(new SimulateAction(action, captureProb));
            }
            else {
                result.add(new SimulateAction(action, standardAction));
            }
        }
        /*
        //TEST - libera il re
        //double kingFreeProb = ;
        if(this.playerTurn == WHITE) {
            for(TablutAction action : actions) {
                if(action.pawn.position.row == kingPosition.row) {
                    if(action.coordinates.row != kingPosition.row || (action.coordinates.row == kingPosition.row && 
                        Math.abs(action.coordinates.row - kingPosition.row) > Math.abs(action.pawn.position.row - kingPosition.row))) {
                            result.add(action);
                        }
                }
                else if(action.pawn.position.column == kingPosition.column) {
                    if(action.coordinates.column != kingPosition.column || (action.coordinates.column == kingPosition.column && 
                        Math.abs(action.coordinates.column - kingPosition.column) > Math.abs(action.pawn.position.column - kingPosition.column))) {
                            result.add(action);
                        }
                }
            }
        }

        //TEST - blocca il re
        if(this.playerTurn == BLACK && !(kingPosition.row == 4 && kingPosition.column == 4)) {
            for(TablutAction action : actions) {
                if(action.pawn.position.row != kingPosition.row && action.pawn.position.column != kingPosition.column) {
                    if(action.coordinates.row == kingPosition.row || action.coordinates.column == kingPosition.column) {
                        result.add(action);
                    }
                }
            }
        }

        //TEST - blocca uscite
        for(TablutAction action : actions) {
            if(getValue(board, action.coordinates) == ESCAPE)
                result.add(action);
        }

                
        //TEST - defender
        for(TablutAction action : actions) {
            if(isDefending(action.coordinates, action.pawn.getPawnType()))
                result.add(action);
        }*/
        return result;
    }

    private boolean isDefending(Coordinates pos, byte defender) {
        if(pos.row - 1 > 0 && pawns[pos.row - 1][pos.column] != EMPTY && !isEnemy(pawns[pos.row - 1][pos.column], defender))
            return true;
        if(pos.row + 1 < BOARD_SIZE && pawns[pos.row + 1][pos.column] != EMPTY && !isEnemy(pawns[pos.row + 1][pos.column], defender))
            return true;
        if(pos.column - 1 > 0 && pawns[pos.row][pos.column - 1] != EMPTY && !isEnemy(pawns[pos.row][pos.column - 1], defender))
            return true;
        if(pos.column + 1 < BOARD_SIZE && pawns[pos.row][pos.column + 1] != EMPTY && !isEnemy(pawns[pos.row][pos.column + 1], defender))
            return true;
        return false;
    }

    public LinkedList<TablutAction> getBestActionFirst(int[] weights) {
        if (whiteWin || blackWin || draw)
            return new LinkedList<>();

        LinkedList<TablutAction> actions = getLegalActions();
        LinkedList<TablutAction> result = new LinkedList<>();
        boolean loosing = false;
        boolean stop = false;
        TablutAction kingAction = null;

        if (actions.isEmpty()) {
            if (playerTurn == WHITE)
                blackWin = true;
            else
                whiteWin = true;
            return result;
        }
        if (playerTurn == BLACK) {
            for (LinkedList<TablutAction> list : actionsMap.get(kingPosition)) {
                for (TablutAction ka : list) {
                    if (isOnPosition(ka.coordinates, ESCAPE)) {
                        kingAction = ka;
                        stop = true;
                        break;
                    }
                }
                if (stop)
                    break;
            }
        }
        if (firstMove) {
            if (playerTurn == WHITE)
                result.add(whiteOpening());
            else {
                result.add(blackOpening(firstAction));
                firstMove = false;
            }
            return result;
        }
        for (TablutAction action : actions) {
            if (isWin(action)) {
                result = new LinkedList<>();
                result.add(action);
                break;
            } else if (isPreventingLoose(action, kingAction)) {
                if (!loosing)
                    result = new LinkedList<>();
                result.add(action);
                loosing = true;
            } else if (!loosing)
                result.addLast(action);
        }

        if (!(result.size() == 1 || loosing))
            result = getFinalActions(result, weights);
        if (result.isEmpty())
            return actions;
        return result;
    }

    public TablutAction whiteOpening() {
        return new TablutAction(new Coordinates(2, 1), new Pawn(WHITE, new Coordinates(2, 4)));
    }

    public TablutAction blackOpening(TablutAction whiteOpening) {
        HashMap<TablutAction, TablutAction> responses = new HashMap<>();
        Coordinates whiteUp = new Coordinates(2, 4);
        Coordinates whiteDown = new Coordinates(3, 4);
        responses.put(new TablutAction(new Coordinates(2, 0), new Pawn(WHITE, whiteUp)),
                new TablutAction(new Coordinates(2, 5), new Pawn(BLACK, new Coordinates(0, 5))));
        responses.put(new TablutAction(new Coordinates(2, 1), new Pawn(WHITE, whiteUp)),
                new TablutAction(new Coordinates(2, 5), new Pawn(BLACK, new Coordinates(0, 5))));
        responses.put(new TablutAction(new Coordinates(2, 2), new Pawn(WHITE, whiteUp)),
                new TablutAction(new Coordinates(2, 5), new Pawn(BLACK, new Coordinates(0, 5))));
        responses.put(new TablutAction(new Coordinates(2, 3), new Pawn(WHITE, whiteUp)),
                new TablutAction(new Coordinates(1, 6), new Pawn(BLACK, new Coordinates(1, 4))));
        responses.put(new TablutAction(new Coordinates(3, 1), new Pawn(WHITE, whiteDown)),
                new TablutAction(new Coordinates(3, 4), new Pawn(BLACK, new Coordinates(3, 8))));
        responses.put(new TablutAction(new Coordinates(3, 2), new Pawn(WHITE, whiteDown)),
                new TablutAction(new Coordinates(3, 4), new Pawn(BLACK, new Coordinates(3, 8))));
        responses.put(new TablutAction(new Coordinates(3, 3), new Pawn(WHITE, whiteDown)),
                new TablutAction(new Coordinates(1, 6), new Pawn(BLACK, new Coordinates(1, 4))));

        if (responses.get(whiteOpening) != null) {
            TablutAction result = responses.get(whiteOpening);
            result.addCapture(getCaptured(result.coordinates, BLACK, result.coordinates.row + 2, result.coordinates.column));
            result.addCapture(getCaptured(result.coordinates, BLACK, result.coordinates.row - 2, result.coordinates.column));
            result.addCapture(getCaptured(result.coordinates, BLACK, result.coordinates.row, result.coordinates.column + 2));
            result.addCapture(getCaptured(result.coordinates, BLACK, result.coordinates.row, result.coordinates.column - 2));
            return result;
        }

        Coordinates position = new Coordinates(whiteOpening.pawn.position.row, whiteOpening.pawn.position.column);
        Coordinates destination = new Coordinates(whiteOpening.coordinates.row, whiteOpening.coordinates.column);

        boolean swap = false;
        boolean columnPos = false;
        if (position.row == 4) {
            int temp = position.row;
            position.row = position.column;
            position.column = temp;
            swap = true;
        }
        if (position.column > 4) {
            position.column = BOARD_SIZE - 1 - position.column;
            columnPos = true;
        }
        if (position.row > 4) {
            position.row = BOARD_SIZE - 1 - position.row;
            columnPos = true;
        }
        boolean mirror = true;

        Directions dir = getDirection(whiteOpening.pawn.position, whiteOpening.coordinates);
        if ((dir == Directions.UP && swap && columnPos) || (dir == Directions.DOWN && swap && !columnPos)
                || (dir == Directions.LEFT && !swap && !columnPos) || (dir == Directions.RIGHT && !swap && columnPos)) {
            mirror = false;
        }

        if(destination.row == whiteOpening.pawn.position.row) 
            destination.column = 4 - Math.abs(whiteOpening.pawn.position.column - destination.column);
        else 
            destination.column = 4 - Math.abs(whiteOpening.pawn.position.row - destination.row);
        destination.row = position.row;

        TablutAction response = responses.get(new TablutAction(destination, new Pawn(WHITE, position)));
        Coordinates resPos = response.pawn.position;
        Coordinates resDest = response.coordinates;
        if (mirror) {
            resPos.column = BOARD_SIZE - 1 - resPos.column;
            resDest.column = BOARD_SIZE - 1 - resDest.column;
        }
        if (swap || columnPos) {
            if (swap) {
                int temp = resPos.row;
                resPos.row = resPos.column;
                resPos.column = temp;
                temp = resDest.row;
                resDest.row = resDest.column;
                resDest.column = temp;
                if (columnPos) {
                    resPos.column = BOARD_SIZE - 1 - resPos.column;
                    resDest.column = BOARD_SIZE - 1 - resDest.column;
                } else {
                    resPos.row = BOARD_SIZE - 1 - resPos.row;
                    resDest.row = BOARD_SIZE - 1 - resDest.row;
                }
            } else {
                resPos.column = BOARD_SIZE - 1 - resPos.column;
                resPos.row = BOARD_SIZE - 1 - resPos.row;
                resDest.row = BOARD_SIZE - 1 - resDest.row;
                resDest.column = BOARD_SIZE - 1 - resDest.column;
            }
        }
        TablutAction result = new TablutAction(resDest, new Pawn(BLACK, resPos));
        result.addCapture(getCaptured(resDest, BLACK, resDest.row + 2, resDest.column));
        result.addCapture(getCaptured(resDest, BLACK, resDest.row - 2, resDest.column));
        result.addCapture(getCaptured(resDest, BLACK, resDest.row, resDest.column + 2));
        result.addCapture(getCaptured(resDest, BLACK, resDest.row, resDest.column - 2));
        return result;
    }

    private ArrayList<Integer> getKingPaths() {
        ArrayList<Integer> kingPaths = new ArrayList<>(Directions.values().length);
        ArrayList<LinkedList<TablutAction>> kingMoves = actionsMap.get(kingPosition);
        if (kingMoves != null)
            for (Directions dir : Directions.values())
                kingPaths.add(dir.value(), kingMoves.get(dir.value()).size());
        return kingPaths;
    }

    // TODO
    private ArrayList<Integer> kingPaths;

    private LinkedList<TablutAction> getFinalActions(LinkedList<TablutAction> actions, int[] weights) {
        LinkedList<TablutAction> result = new LinkedList<>();
        int oldCaptures = 0;
        int oldLoss = 0;
        int oldKingMoves = 0;

        // CAPTURE MAP
        capturesMap = new HashMap<>();

        for (TablutAction a : actions)
            for (Capture c : a.getCaptured())
                updateCaptureMap(capturesMap, c, a.pawn.position, true);
        for (TablutAction a : getLegalActions(this.playerTurn == WHITE ? BLACK : WHITE))
            for (Capture c : a.getCaptured())
                updateCaptureMap(capturesMap, c, a.pawn.position, true);

        for (Map.Entry<Capture, LinkedList<Coordinates>> e : capturesMap.entrySet())
            if (isEnemy(e.getKey().getCaptured().getPawnType(), this.playerTurn))
                oldCaptures++;
            else
                oldLoss++;
        // KINGMOVES
        for (Integer i : getKingPaths())
            oldKingMoves += i;

        LinkedList<TablutAction> first = new LinkedList<>();
        for (TablutAction action : actions) {
            action.setValue(evaluateAction(action, weights, oldCaptures, oldLoss, oldKingMoves));
            if (action.pawn.getPawnType() == KING)
                first.addFirst(action);
            else if (action.getValue() >= 0)
                result.add(action);
        }
        Collections.sort(result);
        for (TablutAction action : first)
            result.addFirst(action);
        if (result.isEmpty() && !actions.isEmpty())
            return actions;
        return result;
    }

    // TODO - change this
    private HashMap<Capture, LinkedList<Coordinates>> captureMapCopy;

    private double evaluateAction(TablutAction action, int[] weights, int oldCaptures, int oldLoss, int oldKingMoves) {
        int newCaptures = 0;
        int newLoss = 0;
        boolean kingCheckmate = false;
        boolean willBeCaptured = false;
        this.newActiveCaptures = 0;
        this.captureMapCopy = new HashMap<>();
        this.kingPaths = getKingPaths();

        for (Map.Entry<Capture, LinkedList<Coordinates>> entry : this.capturesMap.entrySet())
            captureMapCopy.put(entry.getKey(), new LinkedList<>(entry.getValue()));
        if (action.pawn.getPawnType() == KING)
            return 0;

        makeTemporaryAction(action);
        if (action.pawn.getPawnType() != KING)
            kingCheckmate = isKingCheckmate();
        updateActionMap(action, true);
        undoTemporaryAction(action);

        int newKingMoves = 0;
        for (Directions dir : Directions.values())
            newKingMoves += kingPaths.get(dir.value());

        for (Map.Entry<Capture, LinkedList<Coordinates>> entry : captureMapCopy.entrySet())
            if (isEnemy(entry.getKey().getCaptured().getPawnType(), this.playerTurn)
                    && !entry.getValue().contains(action.coordinates))
                newCaptures++;
            else {
                newLoss++;
                if (entry.getKey().getCaptured().position.equals(action.coordinates))
                    willBeCaptured = true;
            }

        int capturesDiff = (newCaptures + newActiveCaptures - oldCaptures);
        int lossDiff = (newLoss - oldLoss);
        int totalDiff = capturesDiff - lossDiff;
        int kingMovesDiff = newKingMoves - oldKingMoves;
        int playerWeight = this.playerTurn == BLACK ? -1 : 1;
        double playerCaptureWeight = this.playerTurn == BLACK ? 2.0 / 3 : 1.0 / 3;

        if (playerTurn == BLACK && kingCheckmate)
            return -1;
        if (action.pawn.getPawnType() == KING)
            return willBeCaptured ? -1 : 0;
        return playerCaptureWeight * weights[Weights.TOTAL_DIFF.value()] * totalDiff
                + weights[Weights.ACTIVE_CAPTURES.value()] * newActiveCaptures
                + playerWeight * weights[Weights.KING_MOVES_DIFF.value()] * kingMovesDiff
                - weights[Weights.WILL_BE_CAPTURED.value()] * (willBeCaptured ? 1 : 0)
                + weights[Weights.KING_CHECKMATE.value()] * (kingCheckmate ? 1 : 0);
    }

    private boolean isKingCheckmate() {
        boolean kingCheckmate = false;
        ArrayList<LinkedList<TablutAction>> kingActions = getPawnActions(kingPosition, KING);
        for (Directions dir : Directions.values()) {
            int initialEscapes = 0;
            if (searchEscape(kingPosition, dir)) {
                if (playerTurn == BLACK) {
                    kingCheckmate = true;
                    break;
                }
                initialEscapes = 1;
            }
            for (TablutAction a : kingActions.get(dir.value())) {
                if (!isOnPosition(a.coordinates, ESCAPE)) {
                    int escapes = initialEscapes;
                    for (Directions dirToEscape : getOtherAxisDirections(dir)) {
                        if (searchEscape(a.coordinates, dirToEscape))
                            escapes++;
                        if (escapes > 1) {
                            kingCheckmate = true;
                            break;
                        }
                    }
                    if (kingCheckmate)
                        break;
                }
            }
        }
        return kingCheckmate;
    }

    private boolean isPreventingLoose(TablutAction action, TablutAction kingAction) {
        byte player = action.pawn.getPawnType();

        if (player == BLACK && kingAction != null) {
            int row = action.coordinates.row;
            int column = action.coordinates.column;
            int kre = kingAction.coordinates.row;
            int krk = kingAction.pawn.position.row;
            int kce = kingAction.coordinates.column;
            int kck = kingAction.pawn.position.column;
            if (row == krk && ((column > kck && column <= kce) || (column < kck && column >= kce)))
                return true;
            if (column == kck && ((row > krk && row <= kre) || (row < krk && row >= kre)))
                return true;
        }
        return false;
    }

    private boolean isWin(TablutAction action) {
        byte player = action.pawn.getPawnType();
        if (player == BLACK) {
            for (Capture capture : action.getCaptured())
                if (pawns[capture.getCaptured().position.row][capture.getCaptured().position.column] == KING)
                    return true;
        } else {
            if (player == KING && isOnPosition(action.coordinates, ESCAPE))
                return true;
            return blackPawns - action.getCaptured().size() == 0;
        }
        return false;
    }

    public LinkedList<TablutAction> getLegalActions() {
        if (blackWin || whiteWin || draw)
            return new LinkedList<TablutAction>();
        return getLegalActions(this.playerTurn);
    }

    public LinkedList<TablutAction> getLegalActions(byte player) {
        LinkedList<TablutAction> actions = new LinkedList<>();
        for (Map.Entry<Coordinates, ArrayList<LinkedList<TablutAction>>> entry : this.actionsMap.entrySet()) {
            Coordinates c = entry.getKey();
            if (getValue(pawns, c) == player || (player == WHITE && getValue(pawns, c) == KING))
                for (LinkedList<TablutAction> l : entry.getValue()) {
                    actions.addAll(l);
                }
        }
        return actions;
    }

    private LinkedList<TablutAction> searchActions(Coordinates coord, byte pawn, int step, boolean row) {
        LinkedList<TablutAction> actions = new LinkedList<>();
        for (int i = (row ? coord.row : coord.column) + step; (step > 0 ? i < BOARD_SIZE : i >= 0); i += step) {
            TablutAction action;
            Coordinates c = new Coordinates(row ? i : coord.row, !row ? i : coord.column);
            action = new TablutAction(c, new Pawn(pawn, coord));
            if (!isLegal(action))
                break;
            Capture captured;
            captured = getCaptured(c, pawn, c.row + 2, c.column);
            if (captured != null) {
                action.addCapture(captured);
            }
            captured = getCaptured(c, pawn, c.row - 2, c.column);
            if (captured != null) {
                action.addCapture(captured);
            }
            captured = getCaptured(c, pawn, c.row, c.column + 2);
            if (captured != null) {
                action.addCapture(captured);
            }
            captured = getCaptured(c, pawn, c.row, c.column - 2);
            if (captured != null) {
                action.addCapture(captured);
            }
            actions.add(action);
        }
        return actions;
    }

    private boolean searchEscape(Coordinates position, Directions dir) {
        int step = dir == Directions.UP || dir == Directions.LEFT ? -1 : 1;
        boolean row = dir == Directions.UP || dir == Directions.DOWN;
        Coordinates c = new Coordinates(position.row, position.column);
        for (int i = (row ? position.row : position.column) + step; step > 0 ? i < BOARD_SIZE : i >= 0; i += step) {
            if (row)
                c.row = i;
            else
                c.column = i;
            if (getValue(pawns, c) != EMPTY)
                return false;
            if (getValue(board, c) == ESCAPE)
                return true;
            if (getValue(board, c) != EMPTY)
                return false;
        }
        return false;
    }

    // change name, updateTempActions
    private void countCaptures(Coordinates coord, byte pawn, Directions dir) {
        if (actionsMap.get(coord) != null)
            for (TablutAction a : actionsMap.get(coord).get(dir.value())) {
                for (Capture c : a.getCaptured())
                    updateCaptureMap(captureMapCopy, c, a.pawn.position, false);
            }
        int step = dir == Directions.UP || dir == Directions.LEFT ? -1 : 1;
        boolean row = dir == Directions.UP || dir == Directions.DOWN;
        int moves = 0;
        for (int i = (row ? coord.row : coord.column) + step; (step > 0 ? i < BOARD_SIZE : i >= 0); i += step) {
            TablutAction action;
            Coordinates c = new Coordinates(row ? i : coord.row, !row ? i : coord.column);
            action = new TablutAction(c, new Pawn(pawn, coord));
            if (!isLegal(action))
                break;
            moves++;
            Capture captured;
            captured = getCaptured(c, pawn, c.row + 2, c.column);
            if (captured != null) {
                updateCaptureMap(captureMapCopy, captured, coord, true);
            }
            captured = getCaptured(c, pawn, c.row - 2, c.column);
            if (captured != null) {
                updateCaptureMap(captureMapCopy, captured, coord, true);
            }
            captured = getCaptured(c, pawn, c.row, c.column + 2);
            if (captured != null) {
                updateCaptureMap(captureMapCopy, captured, coord, true);
            }
            captured = getCaptured(c, pawn, c.row, c.column - 2);
            if (captured != null) {
                updateCaptureMap(captureMapCopy, captured, coord, true);
            }
        }
        if (pawn == KING) {
            kingPaths.set(dir.value(), moves);
        }
    }

    private boolean isLegal(TablutAction action) {
        int nextRow = action.coordinates.row;
        int nextColumn = action.coordinates.column;
        int row = action.pawn.position.row;
        int column = action.pawn.position.column;
        byte pawn = action.pawn.getPawnType();
        return pawns[nextRow][nextColumn] == EMPTY
                && (board[nextRow][nextColumn] == EMPTY || board[nextRow][nextColumn] == ESCAPE
                        || (board[nextRow][nextColumn] == CAMP && board[row][column] == CAMP && pawn == BLACK));
    }

    public void makeAction(TablutAction action) {
        Coordinates pawnPosition = action.pawn.position;
        Coordinates nextPosition = action.coordinates;
        byte pawn = action.pawn.getPawnType();
        if (firstMove)
            firstAction = action;

        pawns[pawnPosition.row][pawnPosition.column] = EMPTY;
        pawns[nextPosition.row][nextPosition.column] = pawn;
        if (pawn == KING) {
            kingPosition = nextPosition;
        }
        if (pawn == KING && isOnPosition(nextPosition, ESCAPE)) {
            whiteWin = true;
        }
        LinkedList<Capture> captures = action.getCaptured();
        if (!captures.isEmpty()) {
            this.drawConditions = new LinkedList<>();
            for (Capture capture : captures) {
                if (pawns[capture.getCaptured().position.row][capture.getCaptured().position.column] == KING)
                    blackWin = true;
                pawns[capture.getCaptured().position.row][capture.getCaptured().position.column] = EMPTY;
                if (playerTurn == WHITE) {
                    blackPawns--;
                } else
                    whitePawns--;
            }
        } else {
            checkDraw();
        }
        updateActionMap(action, false);
        this.drawConditions.add(this);
        if (this.playerTurn == WHITE)
            this.playerTurn = BLACK;
        else
            this.playerTurn = WHITE;
        this.previousAction = action;
    }

    public void makeTemporaryAction(TablutAction action) {
        pawns[action.pawn.position.row][action.pawn.position.column] = EMPTY;
        pawns[action.coordinates.row][action.coordinates.column] = action.pawn.getPawnType();
        for (Capture capture : action.getCaptured()) {
            pawns[capture.getCaptured().position.row][capture.getCaptured().position.column] = EMPTY;
        }
    }

    public void undoTemporaryAction(TablutAction action) {
        pawns[action.coordinates.row][action.coordinates.column] = EMPTY;
        pawns[action.pawn.position.row][action.pawn.position.column] = action.pawn.getPawnType();
        for (Capture capture : action.getCaptured()) {
            pawns[capture.getCaptured().position.row][capture.getCaptured().position.column] = capture.getCaptured()
                    .getPawnType();
        }
    }

    private void checkDraw() {
        for (TablutState state : drawConditions) {
            if (state.equals(this)) {
                draw = true;
                break;
            }
        }
    }

    private Capture getCaptured(Coordinates position, byte pawn, int row, int column) {
        if (row < 0 || column < 0 || row >= BOARD_SIZE || column >= BOARD_SIZE)
            return null;
        if (!isBlocker(row, column, pawn))
            return null;
        Capture captured = null;
        if (row == position.row)
            if (column > position.column)
                captured = new Capture(new Pawn(pawns[row][column - 1], new Coordinates(row, column - 1)));
            else
                captured = new Capture(new Pawn(pawns[row][column + 1], new Coordinates(row, column + 1)));
        else if (column == position.column)
            if (row > position.row)
                captured = new Capture(new Pawn(pawns[row - 1][column], new Coordinates(row - 1, column)));
            else
                captured = new Capture(new Pawn(pawns[row + 1][column], new Coordinates(row + 1, column)));
        else
            return null;
        if (!isEnemy(pawns[captured.getCaptured().position.row][captured.getCaptured().position.column], pawn))
            return null;

        if (pawns[captured.getCaptured().position.row][captured.getCaptured().position.column] == KING
                && !kingCaptured(captured.getCaptured().position, position))
            return null;
        return captured;
    }

    private boolean isBlocker(int row, int column, byte pawn) {
        byte blocker = pawns[row][column];
        if (blocker == KING)
            blocker = WHITE;
        if (pawn == KING)
            pawn = WHITE;
        byte cell = board[row][column];
        if (pawn == blocker || cell == CITADEL)
            return true;
        if (cell == CAMP) {
            if ((row == 0 && column == 4) || (row == 4 && column == 0) || (row == BOARD_SIZE - 1 && column == 4)
                    || (row == 4 && column == BOARD_SIZE - 1)) {
                return false;
            }
            return true;
        }
        return false;
    }

    public boolean isBoardBlocker(Coordinates blocker) {
        byte cell = getValue(board, blocker);
        int row = blocker.row;
        int column = blocker.column;
        if (cell == CITADEL)
            return true;
        if (cell == CAMP) {
            if ((row == 0 && column == 4) || (row == 4 && column == 0) || (row == BOARD_SIZE - 1 && column == 4)
                    || (row == 4 && column == BOARD_SIZE - 1)) {
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean isEnemy(byte enemy, byte pawn) {
        if (enemy == EMPTY || pawn == EMPTY)
            return false;
        if (enemy == KING)
            enemy = WHITE;
        if (pawn == KING)
            pawn = WHITE;
        return pawn != enemy;
    }

    private boolean kingCaptured(Coordinates captured, Coordinates emptyPos) {
        int r = captured.row;
        int c = captured.column;
        if (r < 3 || r > 5 || c < 3 || c > 5)
            return true;
        boolean result = true;
        pawns[emptyPos.row][emptyPos.column] = BLACK;
        if (board[r][c] == CITADEL)
            result = pawns[r + 1][c] == BLACK && pawns[r - 1][c] == BLACK && pawns[r][c + 1] == BLACK
                    && pawns[r][c - 1] == BLACK;
        else if (board[r + 1][c] == CITADEL)
            result = pawns[r - 1][c] == BLACK && pawns[r][c + 1] == BLACK && pawns[r][c - 1] == BLACK;
        else if (board[r - 1][c] == CITADEL)
            result = pawns[r + 1][c] == BLACK && pawns[r][c + 1] == BLACK && pawns[r][c - 1] == BLACK;
        else if (board[r][c + 1] == CITADEL)
            result = pawns[r + 1][c] == BLACK && pawns[r - 1][c] == BLACK && pawns[r][c - 1] == BLACK;
        else if (board[r][c - 1] == CITADEL)
            result = pawns[r + 1][c] == BLACK && pawns[r - 1][c] == BLACK && pawns[r][c + 1] == BLACK;
        pawns[emptyPos.row][emptyPos.column] = EMPTY;
        return result;
    }

    private boolean isOnPosition(Coordinates coordinates, byte position) {
        return board[coordinates.row][coordinates.column] == position;
    }

    public boolean isWhiteWin() {
        return whiteWin;
    }

    public boolean isBlackWin() {
        return blackWin;
    }

    public boolean isDraw() {
        return draw;
    }

    public byte[][] getPawns() {
        return pawns;
    }

    public byte getPlayerTurn() {
        return this.playerTurn;
    }

    public LinkedList<TablutState> getDrawConditions() {
        return this.drawConditions;
    }

    public int getBlackPawns() {
        return blackPawns;
    }

    public int getWhitePawns() {
        return whitePawns;
    }

    public boolean isFirstMove() {
        return firstMove;
    }

    public void setFirstMove(boolean firstMove) {
        this.firstMove = firstMove;
    }

    public TablutAction getFirstAction() {
        return firstAction;
    }

    public TablutAction getPreviousAction() {
        return previousAction;
    }

    public void setPreviousAction(TablutAction previousAction) {
        this.previousAction = previousAction;
    }
}