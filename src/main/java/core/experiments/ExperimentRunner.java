package core.experiments;

import core.algorithm.AbstractMetaheuristic;
import core.algorithm.aco.ACO;
import core.algorithm.aco.ACO.ExecutionMode;
import core.algorithm.aco.Ant;
import core.algorithm.aco.problem.wsn.WSNAnt;
import core.algorithm.aco.problem.wsn.WSNQPheromoneMatrix;
import core.algorithm.dpso.DPSO;
import core.algorithm.ga.*;
import core.algorithm.localsearch.IterationBasedTC;
import core.algorithm.localsearch.SolutionGenerator;
import core.algorithm.localsearch.TerminalCondition;
import core.algorithm.localsearch.TimeBasedTC;
import core.algorithm.sa.*;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.wsn.RandomBitStringGenerator;
import core.problems.wsn.WSNOptimizationProblem;
import core.problems.wsn.WSNProblemGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

interface InstanceGenerator
{
    OptimizationProblem generate(String instanceFile);
}

public class ExperimentRunner {

    // Parameter sweeps that have already been run are switched off here rather than by
    // hacking the loop bounds.
    private static final boolean RUN_COLONY_SIZE_SWEEP = false;
    private static final boolean RUN_EVAPORATION_SWEEP = false;
    private static final boolean RUN_PHEROMONE_SELECTION_SWEEP = true;
    private static final boolean RUN_PSO_W_SWEEP = false;
    private static final boolean RUN_PSO_SIP_SWEEP = true;
    private static final boolean RUN_PSO_SIG_SWEEP = false;
    private static final boolean RUN_PSO_SWARM_SIZE_SWEEP = true;

    /** Initial WSN pheromone. The old WSNQPheromoneMatrix ignored its argument and always used 1.0. */
    private static final double WSN_INITIAL_PHEROMONE = 1.0;

    static void performExperiment(AbstractMetaheuristic algorithm, InstanceGenerator problemGenerator,String instanceFile, String outputFile, int repeatCount)
    {
        OptimizationProblem problem = problemGenerator.generate(instanceFile);
        String prefix= (new File(instanceFile)).getName()+ "   " + algorithm.getName()+ "  ";
        RepeatingMetaHeuristicEngine engine = new RepeatingMetaHeuristicEngine(algorithm, problem,outputFile,prefix,"",repeatCount);
        engine.run();
    }

    static void convergenceExperiment(List<AbstractMetaheuristic> algorithms, OptimizationProblem problem,String outputFile)
    {
        for (AbstractMetaheuristic algorithm:algorithms) {
            algorithm.setConvergeAnalysisFileName(outputFile);
            algorithm.perform(problem);
        }
    }

    /** ACO used for parameter tuning on WSN instances. */
    static AbstractMetaheuristic buildACOforPT(int colonySize, double evaporationRatio, double pheromoneSelectionRate)
    {
        List<Ant> colony = new ArrayList<>();
        for (int i = 0; i < colonySize; i++) {
            colony.add(new WSNAnt(pheromoneSelectionRate));
        }

        return new ACO(new WSNQPheromoneMatrix(WSN_INITIAL_PHEROMONE, colonySize, evaporationRatio), colony,
                new TimeBasedTC(15000), ExecutionMode.ASYNCHRONOUS);
    }

    static void parameterTuningPSOExperiment()
    {
        double[] wArr = {0.2,0.4,0.6,0.8};
        double[] sipArr = {0.1,0.2,0.4,0.8};
        double[] siGArr = {0.1,0.2,0.4,0.8};
        int[] swarmSizes= {20,30,50};

        for (int w = 0; RUN_PSO_W_SWEEP && w < wArr.length; w++) {
            AbstractMetaheuristic alg = buildDPSOforPT(wArr[w],0.2,0.2,30);
            performExperiments(alg,WSNProblemGenerator::generateProblemInstanceFromJson,"./data/wsn/paramtuning_pso","./data/out/paramtuningpso_W.txt",3);
        }

        for (int sip = 0; RUN_PSO_SIP_SWEEP && sip < sipArr.length; sip++) {
            AbstractMetaheuristic alg = buildDPSOforPT(0.6,sipArr[sip],0.2,30);
            performExperiments(alg,WSNProblemGenerator::generateProblemInstanceFromJson,"./data/wsn/paramtuning_pso","./data/out/paramtuningpso_SIP.txt",10);
        }

        for (int sig = 0; RUN_PSO_SIG_SWEEP && sig < siGArr.length; sig++) {
            AbstractMetaheuristic alg = buildDPSOforPT(0.6,0.2,siGArr[sig],30);
            performExperiments(alg,WSNProblemGenerator::generateProblemInstanceFromJson,"./data/wsn/paramtuning_pso","./data/out/paramtuningpso_SIG.txt",10);
        }

        for (int ss = 0; RUN_PSO_SWARM_SIZE_SWEEP && ss < swarmSizes.length; ss++) {
            AbstractMetaheuristic alg = buildDPSOforPT(0.6,0.2,0.2,swarmSizes[ss]);
            performExperiments(alg,WSNProblemGenerator::generateProblemInstanceFromJson,"./data/wsn/paramtuning_pso","./data/out/paramtuningpso_SS.txt",10);
        }

    }

    static void parameterTuningExperiment()
    {
        int[] colonySize = {5,10,15,20};
        double[] evaporationRatios = {0.05,0.1,0.2};
        double[] pheromoneSelectionRates = {0.1,0.2,0.4,0.6};

        for (int cs = 0; RUN_COLONY_SIZE_SWEEP && cs < colonySize.length; cs++) {
            AbstractMetaheuristic alg = buildACOforPT(colonySize[cs],0.1,0.6);
            performExperiments(alg,WSNProblemGenerator::generateProblemInstanceFromJson,"./data/wsn/paramtuning","./data/out/paramtuning_CS.txt",10);
        }

        for (int er = 0; RUN_EVAPORATION_SWEEP && er < evaporationRatios.length; er++) {
            AbstractMetaheuristic alg = buildACOforPT(5,evaporationRatios[er],0.6);
            performExperiments(alg,WSNProblemGenerator::generateProblemInstanceFromJson,"./data/wsn/paramtuning","./data/out/paramtuning_ER.txt",10);
        }

        for (int pr = 0; RUN_PHEROMONE_SELECTION_SWEEP && pr < pheromoneSelectionRates.length; pr++) {
            AbstractMetaheuristic alg = buildACOforPT(5,0.1,pheromoneSelectionRates[pr]);
            performExperiments(alg,WSNProblemGenerator::generateProblemInstanceFromJson,"./data/wsn/paramtuning","./data/out/paramtuning_PR.txt",10);
        }

    }

    static void comparisonExperiment()
    {
        AbstractMetaheuristic algParallelACO= buildParallelACO();
        AbstractMetaheuristic algACO= buildSequentialACO();
        AbstractMetaheuristic algGA= buildGA();
        AbstractMetaheuristic algSA= buildSA();
        AbstractMetaheuristic algDPSO = buildDPSO();
        List<AbstractMetaheuristic> algorithms= List.of(algGA,algDPSO);

        performExperiments(algorithms,WSNProblemGenerator::generateProblemInstanceFromJson,"./data/wsn/reference","./data/out/comparison2.txt",10);
    }

    static void performExperiments(AbstractMetaheuristic algorithm , InstanceGenerator generator, String instanceFolder, String outputFile, int repeatCount)
    {
        File testCaseFile = new File(instanceFolder);
        if (testCaseFile.isFile()) {
            System.out.println("INVALID FOLDER!");
            return;
        }

        for (File f:testCaseFile.listFiles())
        {
            if (!f.isFile())
                continue;
            System.out.println("PERFORMING "+ algorithm.getName() + " on "+ f.getName());
            performExperiment(algorithm,generator,f.getAbsolutePath(),outputFile,repeatCount);
        }
    }

    static void performExperiments(List<AbstractMetaheuristic> algorithms , InstanceGenerator generator, String instanceFolder, String outputFile, int repeatCount)
    {
        for (AbstractMetaheuristic alg:algorithms)
        {
            performExperiments(alg,generator,instanceFolder,outputFile, repeatCount);
        }

    }

    public static void main(String[] args) {

        comparisonExperiment();

        WSNOptimizationProblem problem = WSNProblemGenerator.generateProblemInstanceFromJson("./data/wsn/reference/m_3_k_3_tc_300_dim_600_600_1.json"); // todo: call Generator here


        AbstractMetaheuristic algParallelACO= buildParallelACO();

        AbstractMetaheuristic algACO= buildSequentialACO();

        AbstractMetaheuristic algGA= buildGA();

        AbstractMetaheuristic algSA= buildSA();

        AbstractMetaheuristic algdpso = buildDPSO();

        //convergenceExperiment(List.of(algdpso),problem,"./data/out/convergenceDPSO.txt");
        //performExperiments(algSA,WSNProblemGenerator::generateProblemInstanceFromJson,"./data/wsn/reference","./data/out",3);

        //convergenceExperiment(List.of(algGA),problem,"./data/out/convergenceGA.txt");

        //parameterTuningPSOExperiment();

    }

    private static AbstractMetaheuristic buildSA() {

        int solutionSize= 529;
        SolutionGenerator sg = new RandomBitStringGenerator(solutionSize);
        TerminalCondition terminalCondition = new TimeBasedTC(30000);
        NeighboringFunction nf = new BinaryFlipNF();
        AbstractMetaheuristic alg= new SA(sg,new LinearCooling(200,1,0.02), terminalCondition, new IterationBasedTC(10),nf,new BoltzmanAF());
        return alg;
    }

    private static AbstractMetaheuristic buildGA() {
        int solutionSize= 529;
        SelectionPolicy parentSelP= new TournamentSelection(5, s->-s.objectiveValue());
        SelectionPolicy victimSelP= new TournamentSelection(5, Solution::objectiveValue);
        CrossOverOperator crossOver = new OnePointCrossover();
        MutationOperator mutation = new FlipMutation();
        TerminalCondition terminalCondition = new TimeBasedTC(30000);
        AbstractMetaheuristic alg= new GA(new RandomBitStringGenerator(solutionSize), terminalCondition, parentSelP,victimSelP,crossOver,mutation);
        return alg;
    }

    /**
     * Sequential and parallel ACO share everything except the execution mode, so comparing
     * them measures only the effect of parallelism.
     */
    private static AbstractMetaheuristic buildParallelACO() {
        return buildWsnACO(ExecutionMode.ASYNCHRONOUS);
    }

    private static AbstractMetaheuristic buildSequentialACO() {
        return buildWsnACO(ExecutionMode.SEQUENTIAL);
    }

    private static AbstractMetaheuristic buildWsnACO(ExecutionMode mode) {
        List<Ant> colony = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            colony.add(new WSNAnt(0.2));
        }
        return new ACO(new WSNQPheromoneMatrix(WSN_INITIAL_PHEROMONE, colony.size(), 0.1), colony,
                new TimeBasedTC(30000), mode);
    }

    private static AbstractMetaheuristic buildDPSO() {
        int solutionSize = 529;
        double w = 0.6;
        double siP= 0.1;
        double siG= 0.1;
        AbstractMetaheuristic alg = new DPSO(w,siP,siG,30,new TimeBasedTC(30000),new RandomBitStringGenerator(529));

        return alg;
    }

    private static AbstractMetaheuristic buildDPSOforPT(    double w , double siP, double siG, int ss) {
        int solutionSize = 529;

        AbstractMetaheuristic alg = new DPSO(w,siP,siG,ss,new TimeBasedTC(15000),new RandomBitStringGenerator(solutionSize));

        return alg;
    }
}
