package core.algorithm.nsga;

import core.SimpleOptimizationProblem;
import core.base.OptimizationProblem;
import core.base.Solution;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NDArchive {
    List<Solution> archive;

    public NDArchive() {
        archive = new ArrayList<>();
    }

    public void update(OptimizationProblem problem,List<Solution> solutions)
    {
        for (Solution s:solutions)
        {
            boolean dominated =false;
            for (Solution s2:solutions)
            {
                if (MOEAUtils.dominates(problem,s2,s)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated)
                update(problem,s);
        }
    }

    private void update(OptimizationProblem problem, Solution s) {
        boolean dominated = false;
        List<Solution> dominatedList = new ArrayList<>();
        for (Solution a:archive)
        {
            if (MOEAUtils.dominates(problem,a,s))
            {
                dominated=true;
                break;
            }
            else if (MOEAUtils.dominates(problem,s,a))
            {
                dominatedList.add(a);
            }
        }
        if (!dominated)
            archive.add(s);
        if (!dominatedList.isEmpty())
            archive.removeAll(dominatedList);
    }

    public static void main(String[] args) {

        OptimizationProblem problem = new SimpleOptimizationProblem(new DummyModel());
        problem.addObjective(new DummyFunction());
        problem.addObjective(new DummyFunction());
        problem.addObjective(new DummyFunction());

        List<Solution> solutions = new ArrayList<>();
        solutions.add(new RankedSolution(new IntegerRep(1),new double[]{3.0, 6.0, 10.0}));
        solutions.add(new RankedSolution(new IntegerRep(2),new double[]{3.0, 6.0, 9.0}));
        solutions.add(new RankedSolution(new IntegerRep(3),new double[]{2.0, 4.0, 6.0}));
        solutions.add(new RankedSolution(new IntegerRep(4),new double[]{1.0, 5.0, 8.0}));
        solutions.add(new RankedSolution(new IntegerRep(5),new double[]{0.0, 2.0, 3.0}));
        solutions.add(new RankedSolution(new IntegerRep(6),new double[]{2.0, 4.2, 5.0}));
        solutions.add(new RankedSolution(new IntegerRep(7),new double[]{1.6, 5.3, 6.0}));
        solutions.add(new RankedSolution(new IntegerRep(8),new double[]{1.2, 5.1, 7.0}));
        solutions.add(new RankedSolution(new IntegerRep(9),new double[]{2.0, 5.0, 5.7}));
        solutions.add(new RankedSolution(new IntegerRep(10),new double[]{5.0, 1.0, 3.0}));
        solutions.add(new RankedSolution(new IntegerRep(10),new double[]{6.0, 2.0, 2.0}));
        solutions.add(new RankedSolution(new IntegerRep(10),new double[]{1.0, 1.0, 1.0}));




        NDArchive archive = new NDArchive();

        archive.update(problem,solutions);

        archive.archive.forEach(System.out::println);
        //solutions.forEach(System.out::println);

    }
}
