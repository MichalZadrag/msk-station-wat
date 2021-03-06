package hla13.station.client;


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
import java.util.Random;

public class ClientFederate {

    private RTIambassador rtiamb;
    private ClientAmbassador fedamb;
    private final double timeStep           = 10.0;
    private int idCounter = 0;

    public void runFederate() throws RTIexception{
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

        fedamb = new ClientAmbassador();
        rtiamb.joinFederationExecution( "ClientFederate", "ExampleFederation", fedamb );
        log( "Joined Federation as ClientFederate");

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

        while (fedamb.running && idCounter < 200) {
            advanceTime(randomTime());
            sendCreateClientInteraction(fedamb.federateTime + fedamb.federateLookahead);
            rtiamb.tick();
        }
        advanceTime(randomTime());
        sendFinishInteraction(fedamb.federateTime + fedamb.federateLookahead);
        rtiamb.tick();
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

    private void sendCreateClientInteraction(double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        Random random = new Random();

        byte[] id = EncodingHelpers.encodeInt(idCounter++);
        byte[] fuelType;

        if (random.nextInt(10) >= 4) {
            fuelType = EncodingHelpers.encodeString("ON");
        } else {
            fuelType = EncodingHelpers.encodeString("PETROL");
        }

        byte[] amountOfFuel = EncodingHelpers.encodeInt(random.nextInt(95) + 5);

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CreateClient");
        int idHandle = rtiamb.getParameterHandle( "id", interactionHandle );
        int fuelTypeHandle = rtiamb.getParameterHandle( "fuelType", interactionHandle );
        int amountOfFuelHandle = rtiamb.getParameterHandle( "amountOfFuel", interactionHandle );

        parameters.add(idHandle, id);
        parameters.add(fuelTypeHandle, fuelType);
        parameters.add(amountOfFuelHandle, amountOfFuel);

        LogicalTime time = convertTime( timeStep );
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }

    private void sendFinishInteraction(double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.Finish");

        LogicalTime time = convertTime( timeStep );
        log("Finish Simulation");
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }

    private void publishAndSubscribe() throws RTIexception {
        int createClientHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.CreateClient" );
        rtiamb.publishInteractionClass(createClientHandle);
        int finishHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.Finish" );
        rtiamb.publishInteractionClass(finishHandle);
    }

    private void advanceTime( double timestep ) throws RTIexception
    {
        log("requesting time advance for: " + timestep);
        // request the advance
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime( fedamb.federateTime + timestep );
        rtiamb.timeAdvanceRequest( newTime );
        while( fedamb.isAdvancing )
        {
            rtiamb.tick();
        }
    }

    private double randomTime() {
        Random r = new Random();
        return 1 +(40 * r.nextDouble());
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
        System.out.println( "ClientFederate   : " + message );
    }

    public static void main(String[] args) {
        try {
            new ClientFederate().runFederate();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
    }

}
