package hla13.producerConsumer.carWash;


import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import hla13.StaticVars;
import hla13.producerConsumer.storage.ExternalEvent;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Random;

public class CarWashFederate {

    private RTIambassador rtiamb;
    private CarWashAmbassador fedamb;
    private final double timeStep           = 10.0;
    private String queue = "";
    private int carWashHlaHandle;


    public void runFederate() throws Exception {

        rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        try
        {
            File fom = new File( "station.fed" );
            rtiamb.createFederationExecution( "ExampleFederation",
                    fom.toURI().toURL() );
            log( "Created Federation" );
        }
        catch( FederationExecutionAlreadyExists exists )
        {
            log( "Didn't create federation, it already existed" );
        }
        catch( MalformedURLException urle )
        {
            log( "Exception processing fom: " + urle.getMessage() );
            urle.printStackTrace();
            return;
        }

        fedamb = new CarWashAmbassador();
        rtiamb.joinFederationExecution( "CashWashFederate", "ExampleFederation", fedamb );
        log( "Joined Federation as CarWashFederate");

        rtiamb.registerFederationSynchronizationPoint( StaticVars.READY_TO_RUN, null );

        while( fedamb.isAnnounced == false )
        {
            rtiamb.tick();
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved( StaticVars.READY_TO_RUN );
        log( "Achieved sync point: " +StaticVars.READY_TO_RUN+ ", waiting for federation..." );
        while( fedamb.isReadyToRun == false )
        {
            rtiamb.tick();
        }

        enableTimePolicy();

        publishAndSubscribe();

        registerCarWashObject();

        Random random = new Random();

        while (fedamb.running) {
            double timeToAdvance = fedamb.federateTime + timeStep + ((50 * random.nextDouble()) + 10);
            advanceTime(timeToAdvance);

            if(fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for(ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    this.addToQueue(externalEvent.getId());
                }
                fedamb.externalEvents.clear();
            }

            if(fedamb.grantedTime == timeToAdvance) {
                timeToAdvance += fedamb.federateLookahead;
                log("Updating stock at time: " + timeToAdvance);
                updateHLAObject(timeToAdvance);
                fedamb.federateTime = timeToAdvance;
            }

            sendMoveToCashRegisterInteraction(fedamb.federateTime + fedamb.federateLookahead);

            rtiamb.tick();
        }

        deleteObject(this.carWashHlaHandle);
        rtiamb.resignFederationExecution( ResignAction.NO_ACTION );
        try
        {
            rtiamb.destroyFederationExecution( "ExampleFederation" );
            log( "Destroyed Federation" );
        }
        catch( FederationExecutionDoesNotExist dne )
        {
            log( "No need to destroy federation, it doesn't exist" );
        }
        catch( FederatesCurrentlyJoined fcj )
        {
            log( "Didn't destroy federation, federates still joined" );
        }

    }

    private void deleteObject( int handle ) throws RTIexception
    {
        rtiamb.deleteObjectInstance(handle, (""+System.currentTimeMillis()).getBytes());
    }

    private void sendMoveToCashRegisterInteraction(double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int id = getFromQueue();
        if (id != -1) {
            byte[] idByte = EncodingHelpers.encodeInt(id);

            int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.MoveToCashRegisterFromCarWash");
            int idHandle = rtiamb.getParameterHandle( "id", interactionHandle );

            parameters.add(idHandle, idByte);

            LogicalTime time = convertTime( timeStep );
            rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
        }
    }

    public void addToQueue(int id) {
        this.queue += "#" + id;
        log("Added "+id + " at time: "+ fedamb.federateTime +", current queue: " + this.queue);
    }

    private int getFromQueue() {

        if(this.queue.split("#").length == 1) {
            log("Empty queue");
        } else if (this.queue.split("#").length == 2) {
            int id = Integer.parseInt(this.queue.split("#")[1]);
            this.queue = "";
            log("Removed "+ id + " at time: "+ fedamb.federateTime +", current queue: " + this.queue);
            return id;
        } else {
            int id = Integer.parseInt(this.queue.split("#")[1]);
            this.queue = this.queue.substring(this.queue.indexOf("#", 1));
            log("Removed "+ id + " at time: "+ fedamb.federateTime +", current queue: " + this.queue);
            return id;
        }
        return -1;
    }

    private void waitForUser()
    {
        log( " >>>>>>>>>> Press Enter to Continue <<<<<<<<<<" );
        BufferedReader reader = new BufferedReader( new InputStreamReader(System.in) );
        try
        {
            reader.readLine();
        }
        catch( Exception e )
        {
            log( "Error while waiting for user input: " + e.getMessage() );
            e.printStackTrace();
        }
    }

    private void registerCarWashObject() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.CarWash");
        this.carWashHlaHandle = rtiamb.registerObjectInstance(classHandle);
    }

    private void updateHLAObject(double time) throws RTIexception{
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int classHandle = rtiamb.getObjectClass(carWashHlaHandle);
        int queueHandle = rtiamb.getAttributeHandle( "queue", classHandle );
        byte[] queueValue = EncodingHelpers.encodeString(queue);

        attributes.add(queueHandle, queueValue);
        LogicalTime logicalTime = convertTime( time );
        rtiamb.updateAttributeValues( carWashHlaHandle, attributes, "actualize queue".getBytes(), logicalTime );
    }

    private void advanceTime( double timeToAdvance ) throws RTIexception {
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime( timeToAdvance );
        rtiamb.timeAdvanceRequest( newTime );

        while( fedamb.isAdvancing )
        {
            rtiamb.tick();
        }
    }

    private void publishAndSubscribe() throws RTIexception {

        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.CarWash");
        int queueHandle    = rtiamb.getAttributeHandle( "queue", classHandle );

        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add( queueHandle );

        rtiamb.publishObjectClass(classHandle, attributes);

        int moveToCarWashHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.MoveToCarWash" );
        fedamb.moveToCarWashHandle = moveToCarWashHandle;
        rtiamb.subscribeInteractionClass( moveToCarWashHandle );

        int finishHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.Finish" );
        fedamb.finishHandle = finishHandle;
        rtiamb.subscribeInteractionClass(finishHandle);

        int moveToCashRegisterFromCarWashHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.MoveToCashRegisterFromCarWash" );
        rtiamb.publishInteractionClass(moveToCashRegisterFromCarWashHandle);
    }

    private void enableTimePolicy() throws RTIexception
    {
        LogicalTime currentTime = convertTime( fedamb.federateTime );
        LogicalTimeInterval lookahead = convertInterval( fedamb.federateLookahead );

        this.rtiamb.enableTimeRegulation( currentTime, lookahead );

        while( fedamb.isRegulating == false )
        {
            rtiamb.tick();
        }

        this.rtiamb.enableTimeConstrained();

        while( fedamb.isConstrained == false )
        {
            rtiamb.tick();
        }
    }

    private LogicalTime convertTime( double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTime( time );
    }

    /**
     * Same as for {@link #convertTime(double)}
     */
    private LogicalTimeInterval convertInterval( double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTimeInterval( time );
    }

    private void log( String message )
    {
        System.out.println( "CarWashFederate   : " + message );
    }

    public static void main(String[] args) {
        try {
            new CarWashFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
