package core.problems.coalitionFormation;


import core.utils.random.RNG;
import core.utils.random.RandUtils;

public class MultiCFPGenerator {

    private static final int MAX = 200;
    private static final double TASK_DIFFICULTY = 1.5;

    public static MCFPModel generateMultiCFProblem(int taskCount, int agentCount, int abilityCount, RNG rng)
    {
        int[][] taskRequirements = createTasks(taskCount, abilityCount, rng);
        int[] maxRequirements = calculateMaxRequirements(taskRequirements,abilityCount);
        int[][] agents = createAgents(maxRequirements, agentCount, abilityCount, taskCount, rng);

        MCFPModel mcf = new MCFPModel(agents,taskRequirements);
        return mcf;
    }

    private static int[] calculateMaxRequirements(int[][] taskRequirements, int abilityCount) {
        int[] maxRequirements = new int[abilityCount];

        for(int ability = 0 ; ability<abilityCount ; ability++){
            maxRequirements[ability] = taskRequirements[1][ability];
            for(int task = 2 ; task<taskRequirements.length ; task++){
                if(maxRequirements[ability] < taskRequirements[task][ability]){
                    maxRequirements[ability] = taskRequirements[task][ability];
                }
            }
        }
        return maxRequirements;
    }

    private static int[][] createAgents(int[] maxRequirements, int agentCount, int abilityCount, int taskCount, RNG rng)
    {
        int[][] agents = new int[agentCount][abilityCount+1];
        for(int i = 0 ; i<agentCount ; i++){
            agents[i][0] = rng.randInt(MAX);
            for(int j = 0 ; j<abilityCount ; j++){
                int minBound = (maxRequirements[j]*taskCount)/agentCount;
                // Drawn from the given rng (not the global one) so that a seed fully determines the instance.
                agents[i][j+1] = (int) (minBound + (minBound*TASK_DIFFICULTY - minBound) * rng.randDouble());
            }
        }
        return agents;
    }

    private static int[][] createTasks(int taskCount, int abilityCount, RNG rng) {
        int[][] taskRequirements = new int[taskCount][abilityCount];
        for(int i = 1 ; i<taskRequirements.length ; i++){
            for(int j = 0 ; j<abilityCount ; j++){
                taskRequirements[i][j] = rng.randInt((MAX))+1;
            }
        }
        return taskRequirements;
    }

    public static void main(String[] args) {
        MultiCFPGenerator.generateMultiCFProblem(2,10,3, RandUtils.getDefaultRNG());
    }

}
