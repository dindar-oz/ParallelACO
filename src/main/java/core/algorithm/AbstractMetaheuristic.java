package core.algorithm;

import core.algorithm.base.SingleObjectiveOA;
import core.algorithm.localsearch.SolutionGenerator;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.utils.FileUtils;

import java.util.List;

public abstract class AbstractMetaheuristic implements SingleObjectiveOA {

    // volatile: parallel algorithms (e.g. ACO) update it from worker threads and read it elsewhere
    protected volatile Solution bestSolution;
    protected SolutionGenerator solutionGenerator;
    private long startTime;
    private long bestAchieveTime;

    private String convergeAnalysisFileName="";


    public void setConvergeAnalysisFileName(String fileName)
    {
        convergeAnalysisFileName=fileName;
    }

    public AbstractMetaheuristic(SolutionGenerator solutionGenerator) {
        this.solutionGenerator = solutionGenerator;
    }

    public Solution getBestSolution() {
        return bestSolution;
    }

    public long getBestAchieveTime() {
        return bestAchieveTime;
    }

    @Override
    public Solution perform(OptimizationProblem problem) {
        init(problem);
        _perform(problem);

        return bestSolution;
    }

    protected void init(OptimizationProblem problem)
    {
        // Instances are reused across repeated runs, so forget the previous run's best.
        bestSolution = null;
        startTime = System.currentTimeMillis();
        if (!convergeAnalysisFileName.isEmpty())
        {
            FileUtils.writeToFile(convergeAnalysisFileName, "", false);
        }
    }

    protected void updateBest(OptimizationProblem problem, List<Solution> solutions)
    {
        for (Solution s:solutions)
            updateBest(problem,s);
    }
    /** Thread-safe: parallel algorithms call this concurrently from several workers. */
    protected synchronized void updateBest(OptimizationProblem problem , Solution solution)
    {
        if ( bestSolution ==null|| problem.objectiveType().betterThan(solution.objectiveValue(),bestSolution.objectiveValue()))
        {
            bestSolution = solution;
            bestAchieveTime = System.currentTimeMillis();
            if (!convergeAnalysisFileName.isEmpty())
            {
                FileUtils.writeToFile(convergeAnalysisFileName, (bestAchieveTime-startTime)+"   "+ bestSolution.objectiveValue()+"\n", true);
            }
        }
    }


    protected abstract void _perform(OptimizationProblem problem);


}
