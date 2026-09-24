package core.algorithm.nsga;


import core.SimpleOptimizationProblem;
import core.base.OptimizationProblem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class NSGANode
{
    RankedSolution solution;
    Set<NSGANode> dominationList;
    int dominatedBy;
    double crowdingDistance;

    public NSGANode(RankedSolution solution) {
        this.solution = solution;
        dominationList = new HashSet<>();
        dominatedBy=0;
    }

    void setCrowdingDistance(double d)
    {
        crowdingDistance = d;
    }

    @Override
    public String toString() {
        return solution + " " + "CD:"+ crowdingDistance;
    }
}

public class NSGA2Ranking {

    List<Set<NSGANode>> rank(List<NSGANode> nodes, OptimizationProblem problem)
    {
        List<Set<NSGANode>> fronts = new ArrayList<>();
        constructFirstFront(problem, nodes, fronts);



        for (int i =1; !fronts.get(i-1).isEmpty(); i++)
        {
            Set<NSGANode> newFront = new HashSet<>();
            for (NSGANode node:fronts.get(i-1))
            {
                for (NSGANode qnode: node.dominationList) {
                    qnode.dominatedBy--;
                    if (qnode.dominatedBy==0)
                    {
                        newFront.add(qnode);
                        qnode.solution.setRank(i+1);
                    }
                }
            }
            fronts.add(newFront);
        }
        return fronts;
    }

    List<NSGANode> calculateCrowdingDistances(OptimizationProblem problem,List<Set<NSGANode>> fronts)
    {
        List<NSGANode> nodes= new ArrayList<>();
        for (Set<NSGANode> front:fronts)
        {
            if (front.isEmpty()) continue;
            front.forEach((x)->{ nodes.add(x);x.setCrowdingDistance(0);});
            for (int o = 0; o < problem.objectiveCount(); o++) {
                int obj =o;
                List<NSGANode> sorted =front.stream().
                        sorted((n1,n2)->Double.compare(n1.solution.objectiveValue(obj),n2.solution.objectiveValue(obj))).
                        collect(Collectors.toList());
                sorted.get(0).setCrowdingDistance(Double.MAX_VALUE);
                sorted.get(sorted.size()-1).setCrowdingDistance(Double.MAX_VALUE);
                for (int i = 1; i < sorted.size()-1; i++) {
                    double cd =  sorted.get(i).crowdingDistance;
                    cd += (sorted.get(i+1).solution.objectiveValue(o)- sorted.get(i-1).solution.objectiveValue(o))/
                          (sorted.get(sorted.size()-1).solution.objectiveValue(o)-sorted.get(0).solution.objectiveValue(o));
                    sorted.get(i).setCrowdingDistance(cd);
                }
            }
        }
        return nodes;
    }

    public List<NSGANode> createNodes(List<RankedSolution> population) {
        List<NSGANode> nsgaNodes = new ArrayList<>();
        for (RankedSolution rs:population
             ) {
            nsgaNodes.add(new NSGANode(rs));
        }
        return nsgaNodes;
    }

    private void constructFirstFront( OptimizationProblem problem, List<NSGANode> nsgaNodes, List<Set<NSGANode>> fronts) {
        Set<NSGANode> front = new HashSet<>();
        fronts.add(front);

        for (int i = 0; i< nsgaNodes.size(); i++)
        {
            NSGANode p = nsgaNodes.get(i);

            int np =0;
            for (NSGANode rs : nsgaNodes)
            {
                if (p==rs)
                    continue;

                if (MOEAUtils.dominates(problem,p.solution,rs.solution))
                    p.dominationList.add(rs);
                else if (MOEAUtils.dominates(problem,rs.solution,p.solution))
                    p.dominatedBy++;
            }

            if (p.dominatedBy ==0)
            {
                p.solution.setRank(1);
                front.add(p);
            }

        }
    }

    public static void main(String[] args) {

        OptimizationProblem problem = new SimpleOptimizationProblem(new DummyModel());
        problem.addObjective(new DummyFunction());
        problem.addObjective(new DummyFunction());
        problem.addObjective(new DummyFunction());

        List<RankedSolution> solutions = new ArrayList<>();
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


        NSGA2Ranking nsga2Ranking = new NSGA2Ranking();

        List<NSGANode> nodes = nsga2Ranking.createNodes(solutions);

        List<Set<NSGANode>> fronts = nsga2Ranking.rank(nodes,problem);
        List<NSGANode> rankedNodes = nsga2Ranking.calculateCrowdingDistances(problem,fronts);

        nodes.forEach(System.out::println);
        //solutions.forEach(System.out::println);

    }
}
