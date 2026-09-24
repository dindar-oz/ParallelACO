package core.problems.wsn;


import core.algorithm.aco.problem.wsn.WSNData;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.representation.BitString;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.IntStream;

public class WSNSolutionImprover implements SolutionImprover {

    private final Random random;
    private final WSN wsn;
    private final WSNData wsnData;
    private final HashMap<Point2D, HashSet<Point2D>> targetToPotentialPositions;
    private final HashMap<Point2D, HashSet<Point2D>> potentialPositionToTargets;
    private final HashMap<Point2D, HashSet<Point2D>> potentialPositionToPotentialPosition;
    private final double IMPROVE_PROBABILITY;
    List<Integer> targets;

    public WSNSolutionImprover(WSN wsn, double improveProbability) {
        random = new SecureRandom();
        this.wsn = wsn;
        wsnData = new WSNData(wsn);
        targets= new ArrayList<>(IntStream.range(0, wsnData.targetsSize()).boxed().toList());
        targetToPotentialPositions = wsn.getTargetToPotentialPositionMap();
        potentialPositionToTargets = wsn.getPotentialPositionToTargetMap();
        potentialPositionToPotentialPosition = wsn.getPotentialPositionToPotentialPositionMap();
        IMPROVE_PROBABILITY = improveProbability;
    }

    /**
     * Maps each potential position with its solutions state. For instance,
     * let potentialPositions = [ a, b, c, d] and solution = 1010. The returned
     * value would be { a:1, b:0, c:1, d:0 }
     * @param potentialPositions
     *      array of potential positions
     * @param solution
     *      the current solution
     * @return LinkedHashMap that holds key (potential position)
     * value (potential position's state) pairs
     */
    private LinkedHashMap<Point2D, Boolean> createPotentialPositionStateMap(Point2D[] potentialPositions, Solution solution) {
        LinkedHashMap<Point2D, Boolean> map = new LinkedHashMap<>();
        BitString bitString = (BitString) solution.getRepresentation();

        for (int i = 0; i < potentialPositions.length; i++)
            map.put(potentialPositions[i], bitString.get(i));

        return map;
    }



    /**
     * Finds each turned on sensors in the solution and maps them with their corresponding index values
     * @param sensors
     *      map of sensors that holds which sensor is on/off
     * @return map of sensors with their index values
     */
    private Map<Point2D, Integer> getTurnedOnSensors(LinkedHashMap<Point2D, Boolean> sensors) {
        Map<Point2D, Integer> map = new HashMap<>();
        int index = 0;
        for (Map.Entry<Point2D, Boolean> item : sensors.entrySet()) {
            if (item.getValue()) {
                map.put(item.getKey(), index);
            }
            index += 1;
        }
        return map;
    }

    /**
     * Finds each turned off sensors in the solution and maps them with their corresponding index values
     * @param sensors
     *      map of sensors that holds which sensor is on/off
     * @return map of sensors with their index values
     */
    private Map<Point2D, Integer> getTurnedOffSensors(LinkedHashMap<Point2D, Boolean> sensors) {
        Map<Point2D, Integer> map = new HashMap<>();
        int index = 0;
        for (Map.Entry<Point2D, Boolean> item : sensors.entrySet()) {
            if (!item.getValue()) {
                map.put(item.getKey(), index);
            }
            index += 1;
        }
        return map;
    }

    /**
     * Finds sensors that can increase the k-coverage
     * @param turnedOnSensors a list of sensors that are turned on
     * @param turnedOffSensors a list of sensors that are turned off
     * @return a list of sensors that will be turned on
     */
    private List<Point2D> getSensorsToTurnOn(List<Point2D> turnedOnSensors, List<Point2D> turnedOffSensors) {
        List<Point2D> sensorToTurnOn = new ArrayList<>();
        // Initially we declared the type of turnedOnSensors as Set and the caller of this method would
        // pass this parameter from a map by mapName.keySet() which returns a set that does not support
        // add and addAll operations that we use below. Since we need to add a sensor to turnedOnSensors list
        // every time we turn on a sensor, we declared this parameter as list.
        Set<Point2D> targets = targetToPotentialPositions.keySet();
        for (Point2D target : targets) {
            int k = getCoverageForTarget(turnedOnSensors, target);
            if (k >= wsn.getK()) {
                continue;
            }
            for (Point2D turnedOffSensor : turnedOffSensors) {
                if (doesSensorContributeToCoverage(turnedOffSensor, target)) {
                    sensorToTurnOn.add(turnedOffSensor);
                    turnedOnSensors.add(turnedOffSensor);
                    k++;

                    if (k >= wsn.getK()) {
                        break;
                    }
                }
            }
        }
        return sensorToTurnOn;
    }

    /**
     * Checks if the given sensors can cover the given target
     * @param turnedOffSensor a sensor
     * @param target a target
     * @return true if the given sensor can cover the given target, false otherwise
     */
    private boolean doesSensorContributeToCoverage(Point2D turnedOffSensor, Point2D target) {
        HashSet<Point2D> sensors = targetToPotentialPositions.get(target);
        return sensors.contains(turnedOffSensor);
    }

    private int getCoverageForTarget(List<Point2D> turnedOnSensors, Point2D target) {
        HashSet<Point2D> sensors = targetToPotentialPositions.get(target);
        int k = 0;
        for (Point2D sensor : turnedOnSensors) {
            if (sensors.contains(sensor)) {
                k++;
            }
        }
        return k;
    }

    public
    int[] getSensorsToTurnOnFast(BitString bs) {
        HashSet<Integer> sensors = bs.ones();
        List<Integer> idles = new ArrayList<>(bs.zeros().stream().toList());
        Collections.shuffle(targets);

        HashSet<Integer> sensorToTurnOn = new HashSet<>();

        for (Integer t : targets) { // Coverage additions
            int k = wsnData.coverage(t,sensors);
            if (k >= wsnData.getK()) {
                continue;
            }
            for (Integer is:idles) {
                if ( wsnData.getCoveringPositions(t).contains(is))
                {
                    sensorToTurnOn.add(is);
                    sensors.add(is);
                    k++;

                    if (k >= wsnData.getK()) {
                        break;
                    }
                }
            }
        }
        if (sensorToTurnOn.isEmpty())
        {
            for (Integer s:sensors) // Connectivity additions
            {
                if (wsnData.connectivity(s,sensors)>=wsnData.getM())
                    continue;

                int[][] arr =wsnData.getConnectedPositions(s).stream().
                        filter(x->!sensors.contains(x)).
                        map(x->new int[]{x,wsnData.connectivity(x,sensors)}).sorted(Comparator.comparingInt(x->x[1])).
                        toArray(int[][]::new);

                // Every neighbour already active: nothing can be switched on for this sensor.
                if (arr.length > 0)
                    sensorToTurnOn.add(arr[arr.length-1][0]);
            }
        }
        return sensorToTurnOn.stream().mapToInt(x->x).toArray();
    }


    /**
     * The heuristic that increases the k-coverage
     * <p>
     * Calculates each target's coverage (k) value and if the k value is lower
     * than the aimed k, it turns on sensors to increase the k-coverage
     * @param sequence current solution's bitstring sequence
     * @param turnedOnSensors map that holds each turned on sensor and their index in the solution
     * @param turnedOffSensors map that holds each turned off sensor and their index in the solution
     */
    private void improveKCoverage(BitString sequence, Map<Point2D, Integer> turnedOnSensors, Map<Point2D, Integer> turnedOffSensors) {
        List<Point2D> shuffledTurnedOffSensors = new ArrayList<>(turnedOffSensors.keySet());
        // check getSensorsToTurnOn() method to see why we create the list of turnedOnSensors
        List<Point2D> listOfTurnedOnSensors = new ArrayList<>(turnedOnSensors.keySet());
        List<Point2D> sensorsToTurnOn = getSensorsToTurnOn(listOfTurnedOnSensors, shuffledTurnedOffSensors);
        for (Point2D sensor : sensorsToTurnOn) {
            sequence.set(turnedOffSensors.get(sensor), true);
        }
    }

    @Override
    public Solution improve(OptimizationProblem problem, Solution solution) {
        LinkedHashMap<Point2D, Boolean> potentialPositionStateMap = createPotentialPositionStateMap(wsn.getPotentialPositions(), solution);
        Map<Point2D, Integer> turnedOnSensors = getTurnedOnSensors(potentialPositionStateMap);
        Map<Point2D, Integer> turnedOffSensors = getTurnedOffSensors(potentialPositionStateMap);
        BitString improvedSequence = (BitString) solution.getRepresentation();

        improveKCoverage(improvedSequence, turnedOnSensors, turnedOffSensors);

        return new BitStringSolution(improvedSequence, problem.objectiveValue(improvedSequence));
    }


    public Solution improveFast(OptimizationProblem problem, Solution solution) {
        BitString bs= (BitString) solution.getRepresentation();
        int[] sensorsToTurnOn= getSensorsToTurnOnFast(bs);
        for (int i:sensorsToTurnOn)
        {
            bs.flip(i);
        }

        return new BitStringSolution(bs, problem.objectiveValue(bs));
    }

    @Override
    public List<Solution> improveAll(OptimizationProblem problem, List<Solution> solutions) {
        List<Solution> improvedSolutions = new ArrayList<>();
        for (Solution solution : solutions) {
            double probability = random.nextDouble();
            if (probability < IMPROVE_PROBABILITY) {
                improvedSolutions.add(improve(problem, solution));
            }
            else {
                improvedSolutions.add(solution);
            }
        }
        return improvedSolutions;
    }
}
