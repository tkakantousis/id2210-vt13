package common.simulation.scenarios;

import se.sics.kompics.p2p.experiment.dsl.SimulationScenario;

@SuppressWarnings("serial")
public class Scenario1 extends Scenario {

    private static SimulationScenario scenario = new SimulationScenario() {
        {

            StochasticProcess process1 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(1, Operations.peerJoin(5), uniform(13));
                }
            };
            StochasticProcess process2 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(50));
                    raise(99, Operations.peerJoin(5), uniform(13));
                }
            };
           
            StochasticProcess process21 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(50));
                    raise(10, Operations.peerJoin(5), uniform(13));
                }
            };
            StochasticProcess process4 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(50));
                    raise(100, Operations.addIndexEntry(), uniform(13));
                }
            };
            StochasticProcess process5 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(150, Operations.addIndexEntry(), uniform(13));
                }
            };
            StochasticProcess process6 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(900, Operations.addIndexEntry(), uniform(13));
                }
            };
            StochasticProcess process7 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(100, Operations.addIndexEntry(), uniform(13));
                }
            };
            StochasticProcess process8 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(100));
                    raise(500, Operations.addIndexEntry(), uniform(13));
                }
            };
            process1.start();
            process2.startAfterTerminationOf(2000, process1);
            //process21.startAfterTerminationOf(50000, process1);
            process4.startAfterTerminationOf(50000, process2);
            //process5.startAfterTerminationOf(40000, process4);
            //process6.startAfterTerminationOf(40000, process5);
//            process7.startAfterTerminationOf(40000, process6);
//            process8.startAfterTerminationOf(40000, process7);
            
        }
    };

//-------------------------------------------------------------------
    public Scenario1() {
        super(scenario);
    }
}
