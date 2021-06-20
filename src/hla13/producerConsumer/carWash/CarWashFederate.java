package hla13.producerConsumer.carWash;


import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import hla13.StaticVars;
import hla13.producerConsumer.distributor.DistributorAmbassador;
import hla13.producerConsumer.storage.ExternalEvent;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

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
        rtiamb.joinFederationExecution( "DistributorFederate", "ExampleFederation", fedamb );
        log( "Joined Federation as DistributorFederate");

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

        registerDistributorObject();

        while (fedamb.running) {
            double timeToAdvance = fedamb.federateTime + timeStep;
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

            sendInteraction(fedamb.federateTime + fedamb.federateLookahead);

            rtiamb.tick();
        }

    }

    private void sendInteraction(double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        byte[] id = EncodingHelpers.encodeInt(getFromQueue());

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.MoveToCashRegisterFromDistributor");
        int idHandle = rtiamb.getParameterHandle( "id", interactionHandle );

        parameters.add(idHandle, id);

        LogicalTime time = convertTime( timeStep );
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
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

    private void registerDistributorObject() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Distributor");
        this.distributorHlaHandle = rtiamb.registerObjectInstance(classHandle);
    }

    private void updateHLAObject(double time) throws RTIexception{
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int classHandle = rtiamb.getObjectClass(distributorHlaHandle);
        int queueHandle = rtiamb.getAttributeHandle( "queue", classHandle );
        byte[] queueValue = EncodingHelpers.encodeString(queue);

        attributes.add(queueHandle, queueValue);
        LogicalTime logicalTime = convertTime( time );
        rtiamb.updateAttributeValues( distributorHlaHandle, attributes, "actualize queue".getBytes(), logicalTime );
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

        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Distributor");
        int queueHandle    = rtiamb.getAttributeHandle( "queue", classHandle );

        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add( queueHandle );

        rtiamb.publishObjectClass(classHandle, attributes);

        int createClientHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.CreateClient" );
        fedamb.createClientHandle = createClientHandle;
        rtiamb.subscribeInteractionClass( createClientHandle );

        int moveToCashRegisterFromDistributorHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.MoveToCashRegisterFromDistributor" );
        rtiamb.publishInteractionClass(moveToCashRegisterFromDistributorHandle);
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
        System.out.println( "DistributorFederate   : " + message );
    }

    public static void main(String[] args) {
        try {
            new CarWashFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
