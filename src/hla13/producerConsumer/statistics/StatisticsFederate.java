package hla13.producerConsumer.statistics;

import hla.rti.*;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

public class StatisticsFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private StatisticsAmbassador fedamb;

    public void runFederate() throws Exception {
        rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        try {
            File fom = new File("station.fed");
            rtiamb.createFederationExecution("ExampleFederation",
                    fom.toURI().toURL());
            log("Created Federation");
        } catch (FederationExecutionAlreadyExists exists) {
            log("Didn't create federation, it already existed");
        } catch (MalformedURLException urle) {
            log("Exception processing fom: " + urle.getMessage());
            urle.printStackTrace();
            return;
        }

        fedamb = new StatisticsAmbassador();
        rtiamb.joinFederationExecution("StatisticsFederate", "ExampleFederation", fedamb);
        log("Joined Federation as " + "StatisticsFederate");

        rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

        while (fedamb.isAnnounced == false) {
            rtiamb.tick();
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (fedamb.isReadyToRun == false) {
            rtiamb.tick();
        }


        enableTimePolicy();

        publishAndSubscribe();
        log("Published and Subscribed");


        while (fedamb.running) {
            advanceTime(1.0);
            rtiamb.tick();
        }

        rtiamb.resignFederationExecution(ResignAction.NO_ACTION);
        log("Resigned from Federation");

        try {
            rtiamb.destroyFederationExecution("ExampleFederation");
            log("Destroyed Federation");
        } catch (FederationExecutionDoesNotExist dne) {
            log("No need to destroy federation, it doesn't exist");
        } catch (FederatesCurrentlyJoined fcj) {
            log("Didn't destroy federation, federates still joined");
        }
    }

    private void waitForUser() {
        log(" >>>>>>>>>> Press Enter to Continue <<<<<<<<<<");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (Exception e) {
            log("Error while waiting for user input: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void advanceTime(double timestep) throws RTIexception {
        // request the advance
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(newTime);
        while (fedamb.isAdvancing) {
            rtiamb.tick();
        }
    }

    private void publishAndSubscribe() throws RTIexception {

        // Publkowanie obiektu SimObject z atrybutem state

        int simObjectClassHandle = rtiamb
                .getObjectClassHandle("ObjectRoot.CashRegister");
        int stateHandle = rtiamb.getAttributeHandle("queue", simObjectClassHandle);

        AttributeHandleSet attributes = RtiFactoryFactory.getRtiFactory()
                .createAttributeHandleSet();
        attributes.add(stateHandle);

        rtiamb.subscribeObjectClassAttributes(simObjectClassHandle, attributes);

        // Zapisanie do inteakcji ko�cz�cej
//        int interactionHandle = rtiamb
//                .getInteractionClassHandle("InteractionRoot.Finish");
        // Dodanie mapowania interakcji na uchwyt
//		HandlersHelper.addInteractionClassHandler("InteractionRoot.Finish",
//                interactionHandle);

//        rtiamb.subscribeInteractionClass(interactionHandle);
    }

    private void enableTimePolicy() throws RTIexception {
        // NOTE: Unfortunately, the LogicalTime/LogicalTimeInterval create code is
        //       Portico specific. You will have to alter this if you move to a
        //       different RTI implementation. As such, we've isolated it into a
        //       method so that any change only needs to happen in a couple of spots
        LogicalTime currentTime = convertTime(fedamb.federateTime);
        LogicalTimeInterval lookahead = convertInterval(fedamb.federateLookahead);

        ////////////////////////////
        // enable time regulation //
        ////////////////////////////
        this.rtiamb.enableTimeRegulation(currentTime, lookahead);

        // tick until we get the callback
        while (fedamb.isRegulating == false) {
            rtiamb.tick();
        }

        /////////////////////////////
        // enable time constrained //
        /////////////////////////////
        this.rtiamb.enableTimeConstrained();

        // tick until we get the callback
        while (fedamb.isConstrained == false) {
            rtiamb.tick();
        }
    }

    private LogicalTime convertTime(double time) {
        // PORTICO SPECIFIC!!
        return new DoubleTime(time);
    }

    /**
     * Same as for {@link #convertTime(double)}
     */
    private LogicalTimeInterval convertInterval(double time) {
        // PORTICO SPECIFIC!!
        return new DoubleTimeInterval(time);
    }


    private void log(String message) {
        System.out.println("StatisticsFederate  : " + message);
    }

    public static void main(String[] args) {
        StatisticsFederate sf = new StatisticsFederate();

        try {
            sf.runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
