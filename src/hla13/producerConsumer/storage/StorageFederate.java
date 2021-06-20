package hla13.producerConsumer.storage;


import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import hla13.StaticVars;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Collections;

public class StorageFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private StorageAmbassador fedamb;
    private final double timeStep           = 10.0;
    private int stock                       = 10;
    private int storageHlaHandle;


    public void runFederate() throws Exception {

        rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        try
        {
            File fom = new File( "client-consumer.fed" );
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

        fedamb = new StorageAmbassador();
        rtiamb.joinFederationExecution( "CashRegisterFederate", "ExampleFederation", fedamb );
        log( "Joined Federation as CashRegisterFederate");

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

        registerStorageObject();

        while (fedamb.running) {
            double timeToAdvance = fedamb.federateTime + timeStep;
            advanceTime(timeToAdvance);

            if(fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for(ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case CREATE_CLIENT:
                            this.addToStock(externalEvent.getId());
                            break;

                        case GET:
                            this.getFromStock(externalEvent.getId());
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            if(fedamb.grantedTime == timeToAdvance) {
                timeToAdvance += fedamb.federateLookahead;
                log("Updating stock at time: " + timeToAdvance);
                updateHLAObject(timeToAdvance);
                fedamb.federateTime = timeToAdvance;
            }

            rtiamb.tick();
        }

    }

    public void addToStock(int qty) {
        this.stock += qty;
        log("Added "+ qty + " at time: "+ fedamb.federateTime +", current stock: " + this.stock);
    }

    public void getFromStock(int qty) {

        if(this.stock - qty < 0) {
            log("Not enough product at stock");
        }
        else {
            this.stock -= qty;
            log("Removed "+ qty + " at time: "+ fedamb.federateTime +", current stock: " + this.stock);
        }


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

    private void registerStorageObject() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Storage");
        this.storageHlaHandle = rtiamb.registerObjectInstance(classHandle);
    }

    private void updateHLAObject(double time) throws RTIexception{
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int classHandle = rtiamb.getObjectClass(storageHlaHandle);
        int stockHandle = rtiamb.getAttributeHandle( "stock", classHandle );
        byte[] stockValue = EncodingHelpers.encodeInt(stock);

        attributes.add(stockHandle, stockValue);
        LogicalTime logicalTime = convertTime( time );
        rtiamb.updateAttributeValues( storageHlaHandle, attributes, "actualize stock".getBytes(), logicalTime );
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

        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Storage");
        int stockHandle    = rtiamb.getAttributeHandle( "stock", classHandle );

        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add( stockHandle );

        rtiamb.publishObjectClass(classHandle, attributes);

        int addProductHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.AddProduct" );
        fedamb.addProductHandle = addProductHandle;
        rtiamb.subscribeInteractionClass( addProductHandle );

        int getProductHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.GetProduct" );
        fedamb.getProductHandle = getProductHandle;
        rtiamb.subscribeInteractionClass( getProductHandle );
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
        System.out.println( "CashRegisterFederate   : " + message );
    }

    public static void main(String[] args) {
        try {
            new StorageFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
