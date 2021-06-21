package hla13.producerConsumer.distributor;


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

public class DistributorFederate {

    private RTIambassador rtiamb;
    private DistributorAmbassador fedamb;
    private final double timeStep = 1.0;
    private String queueON = "";
    private String queueONFedTime = "";
    private String queueONFuelingTime = "";
    private String queuePetrol = "";
    private String queuePetrolFedTime = "";
    private String queuePetrolFuelingTime = "";
    private double totalqueueON = 0.0;
    private double totalqueuePetrol = 0.0;
    private int distributorHlaHandle;


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

        fedamb = new DistributorAmbassador();
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
//                    fedamb.federateTime = externalEvent.getTime();
                    this.addToQueue(externalEvent);
                }
                fedamb.externalEvents.clear();
            }

//            System.out.println(fedamb.grantedTime);
//            System.out.println(fedamb.federateTime);
//            sendInteractions(fedamb.grantedTime + fedamb.federateLookahead);

            if(fedamb.grantedTime == timeToAdvance) {
                timeToAdvance += fedamb.federateLookahead;
                log("Updating stock at time: " + timeToAdvance);
                updateHLAObject(timeToAdvance);
                fedamb.federateTime = timeToAdvance;
            }
            System.out.println(fedamb.grantedTime);
            System.out.println(fedamb.federateTime);
            sendInteractions(fedamb.federateTime + (2 * fedamb.federateLookahead));

            rtiamb.tick();
        }

    }

    private void sendInteractions(double fedTime) throws RTIexception {
        sendMoveToCashRegisterFromONDistributor(fedTime);
        sendMoveToCashRegisterFromPetrolDistributor(fedTime);
    }

    private void sendMoveToCashRegisterFromONDistributor(double fedTime) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        String[] client = tryGetFromQueueON(fedTime);

        while (Integer.parseInt(client[0]) != -1) {
            byte[] idByte = EncodingHelpers.encodeInt(Integer.parseInt(client[0]));

            int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.MoveToCashRegisterFromDistributor");
            int idHandle = rtiamb.getParameterHandle( "id", interactionHandle );

            parameters.add(idHandle, idByte);

            LogicalTime time = convertTime( Double.valueOf(client[1]) );
            rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );

            client = tryGetFromQueueON(fedTime);
        }
    }

    private void sendMoveToCashRegisterFromPetrolDistributor(double fedTime) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        String[] client = tryGetFromQueuePetrol(fedTime);

        while (Integer.parseInt(client[0]) != -1) {
            byte[] idByte = EncodingHelpers.encodeInt(Integer.parseInt(client[0]));

            int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.MoveToCashRegisterFromDistributor");
            int idHandle = rtiamb.getParameterHandle( "id", interactionHandle );

            parameters.add(idHandle, idByte);

            LogicalTime time = convertTime( Double.valueOf(client[1]) );
            rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );

            client = tryGetFromQueuePetrol(fedTime);
        }
    }

    public void addToQueue(ExternalEvent externalEvent) {
        switch (externalEvent.getFuelType()) {
            case "ON":
                this.queueON += "#" + externalEvent.getId();
                this.queueONFuelingTime += "#" + (externalEvent.getAmountOfFuel() * 0.15);
                this.queueONFedTime += "#" + ((externalEvent.getAmountOfFuel() * 0.15) + fedamb.federateTime + totalqueueON);
                this.totalqueueON += externalEvent.getAmountOfFuel() * 0.15;
                log("Added "+ externalEvent.getId() + " at time: "+ fedamb.federateTime +", current queue ON : " + this.queueON);
                break;
            case "PETROL":
                this.queuePetrol += "#" + externalEvent.getId();
                this.queuePetrolFuelingTime += "#" + (externalEvent.getAmountOfFuel() * 0.1);
                this.queuePetrolFedTime += "#" + ((externalEvent.getAmountOfFuel() * 0.1) + fedamb.federateTime + totalqueuePetrol);
                this.totalqueuePetrol += externalEvent.getAmountOfFuel() * 0.1;
                log("Added " + externalEvent.getId() + " at time: "+ fedamb.federateTime +", current queue Petrol : " + this.queuePetrol);
                break;
            default:
                break;
        }
    }

    private String[] tryGetFromQueueON(double fedTime) {

        if(this.queueON.split("#").length == 1) {
            log("Empty queue");
        } else if (this.queueON.split("#").length == 2) {
            System.out.println(this.queueONFedTime.split("#")[1]);
            if (Double.valueOf(this.queueONFedTime.split("#")[1]) <= fedTime) {
                String id = this.queueON.split("#")[1];
                String time = this.queueONFedTime.split("#")[1];
                this.queueON = "";
                this.queueONFedTime = "";
                this.totalqueueON -= Double.valueOf(this.queueONFuelingTime.split("#")[1]);
                this.queueONFuelingTime = "";
                log("Removed "+ id + " at time: "+ fedamb.federateTime +", current queue ON : " + this.queueON);
                return new String[] {id, time};
            }
        } else {
            if (Double.valueOf(this.queueONFedTime.split("#")[1]) <= fedTime) {
                String id = this.queueON.split("#")[1];
                String time = this.queueONFedTime.split("#")[1];
                this.queueON = this.queueON.substring(this.queueON.indexOf("#", 1));
                this.queueONFedTime = this.queueONFedTime.substring(this.queueONFedTime.indexOf("#", 1));
                this.totalqueueON -= Double.valueOf(this.queueONFuelingTime.split("#")[1]);
                this.queueONFuelingTime = this.queueONFuelingTime.substring(this.queueONFuelingTime.indexOf("#", 1));
                log("Removed "+ id + " at time: "+ fedamb.federateTime +", current queue ON : " + this.queueON);
                return new String[] {id, time};
            }
        }
        return new String[] {"-1", ""};
    }

    private String[] tryGetFromQueuePetrol(double fedTime) {

        if(this.queuePetrol.split("#").length == 1) {
            log("Empty queue");
        } else if (this.queuePetrol.split("#").length == 2) {
            System.out.println(this.queuePetrolFedTime.split("#")[1]);
            if (Double.valueOf(this.queuePetrolFedTime.split("#")[1]) <= fedTime) {
                String id = this.queuePetrol.split("#")[1];
                String time = this.queuePetrolFedTime.split("#")[1];
                this.queuePetrol = "";
                this.queuePetrolFedTime = "";
                this.totalqueuePetrol -= Double.valueOf(this.queuePetrolFuelingTime.split("#")[1]);
                this.queuePetrolFuelingTime = "";
                log("Removed "+ id + " at time: "+ fedamb.federateTime +", current queue Petrol : " + this.queuePetrol);
                return new String[] {id, time};
            }
        } else {
            if (Double.valueOf(this.queuePetrolFedTime.split("#")[1]) <= fedTime) {
                String id = this.queuePetrol.split("#")[1];
                String time = this.queuePetrolFedTime.split("#")[1];
                this.queuePetrol = this.queuePetrol.substring(this.queuePetrol.indexOf("#", 1));
                this.queuePetrolFedTime = this.queuePetrolFedTime.substring(this.queuePetrolFedTime.indexOf("#", 1));
                this.totalqueuePetrol -= Double.valueOf(this.queuePetrolFuelingTime.split("#")[1]);
                this.queuePetrolFuelingTime = this.queuePetrolFuelingTime.substring(this.queuePetrolFuelingTime.indexOf("#", 1));
                log("Removed "+ id + " at time: "+ fedamb.federateTime +", current queue Petrol: " + this.queuePetrol);
                return new String[] {id, time};
            }
        }
        return new String[] {"-1", ""};
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
        int queueONHandle = rtiamb.getAttributeHandle( "queueON", classHandle );
        byte[] queueONValue = EncodingHelpers.encodeString(queueON);
        int queuePetrolHandle = rtiamb.getAttributeHandle( "queuePetrol", classHandle );
        byte[] queuePetrolValue = EncodingHelpers.encodeString(queuePetrol);
        int queueONFedTimeHandle = rtiamb.getAttributeHandle( "queueONFedTime", classHandle );
        byte[] queueONFedTimeValue = EncodingHelpers.encodeString(queueONFedTime);
        int queueONFuelingTimeHandle = rtiamb.getAttributeHandle( "queueONFuelingTime", classHandle );
        byte[] queueONFuelingTimeValue = EncodingHelpers.encodeString(queueONFuelingTime);
        int queuePetrolFedTimeHandle = rtiamb.getAttributeHandle( "queuePetrolFedTime", classHandle );
        byte[] queuePetrolFedTimeValue = EncodingHelpers.encodeString(queuePetrolFedTime);
        int queuePetrolFuelingTimeHandle = rtiamb.getAttributeHandle( "queuePetrolFuelingTime", classHandle );
        byte[] queuePetrolFuelingTimeValue = EncodingHelpers.encodeString(queuePetrolFuelingTime);
        int totalqueueONHandle = rtiamb.getAttributeHandle( "totalqueueON", classHandle );
        byte[] totalqueueONValue = EncodingHelpers.encodeDouble(totalqueueON);
        int totalqueuePetrolHandle = rtiamb.getAttributeHandle( "totalqueuePetrol", classHandle );
        byte[] totalqueuePetrolValue = EncodingHelpers.encodeDouble(totalqueuePetrol);

        attributes.add(queueONHandle, queueONValue);
        attributes.add(queuePetrolHandle, queuePetrolValue);
        attributes.add(queueONFedTimeHandle, queueONFedTimeValue);
        attributes.add(queueONFuelingTimeHandle, queueONFuelingTimeValue);
        attributes.add(queuePetrolFedTimeHandle, queuePetrolFedTimeValue);
        attributes.add(queuePetrolFuelingTimeHandle, queuePetrolFuelingTimeValue);
        attributes.add(totalqueueONHandle, totalqueueONValue);
        attributes.add(totalqueuePetrolHandle, totalqueuePetrolValue);
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
        int queueONHandle    = rtiamb.getAttributeHandle( "queueON", classHandle );
        int queueONFedTimeHandle    = rtiamb.getAttributeHandle( "queueONFedTime", classHandle );
        int queueONFuelingTimeHandle    = rtiamb.getAttributeHandle( "queueONFuelingTime", classHandle );
        int queuePetrolHandle    = rtiamb.getAttributeHandle( "queuePetrol", classHandle );
        int queuePetrolFedTimeHandle    = rtiamb.getAttributeHandle( "queuePetrolFedTime", classHandle );
        int queuePetrolFuelingTimeHandle    = rtiamb.getAttributeHandle( "queuePetrolFuelingTime", classHandle );
        int totalqueueONHandle    = rtiamb.getAttributeHandle( "totalqueueON", classHandle );
        int totalqueuePetrolHandle    = rtiamb.getAttributeHandle( "totalqueuePetrol", classHandle );

        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add( queueONHandle );
        attributes.add( queueONFedTimeHandle );
        attributes.add( queueONFuelingTimeHandle );
        attributes.add( queuePetrolHandle );
        attributes.add( queuePetrolFedTimeHandle );
        attributes.add( queuePetrolFuelingTimeHandle );
        attributes.add( totalqueueONHandle );
        attributes.add( totalqueuePetrolHandle );

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
            new DistributorFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
