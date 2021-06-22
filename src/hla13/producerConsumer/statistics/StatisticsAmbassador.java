package hla13.producerConsumer.statistics;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import hla13.StaticVars;
import org.portico.impl.hla13.types.DoubleTime;

public class StatisticsAmbassador extends NullFederateAmbassador {

	protected boolean running = true;

    protected double federateTime        = 0.0;
    protected double federateLookahead   = 1.0;
    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;


    public void timeRegulationEnabled( LogicalTime theFederateTime )
    {
        this.federateTime = convertTime( theFederateTime );
        this.isRegulating = true;
    }

    public void timeConstrainedEnabled( LogicalTime theFederateTime )
    {
        this.federateTime = convertTime( theFederateTime );
        this.isConstrained = true;
    }


    public void synchronizationPointRegistrationFailed( String label )
    {
        log( "Failed to register sync point: " + label );
    }

    public void synchronizationPointRegistrationSucceeded( String label )
    {
        log( "Successfully registered sync point: " + label );
    }

    public void announceSynchronizationPoint( String label, byte[] tag )
    {
        log( "Synchronization point announced: " + label );
        if( label.equals(StaticVars.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    public void federationSynchronized( String label )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(StaticVars.READY_TO_RUN) )
            this.isReadyToRun = true;
    }


	public void receiveInteraction(int interactionClass,
			ReceivedInteraction theInteraction, byte[] tag) {

		receiveInteraction(interactionClass, theInteraction, tag, null, null);
	}

	public void receiveInteraction(int interactionClass,
			ReceivedInteraction theInteraction, byte[] tag,
			LogicalTime theTime, EventRetractionHandle eventRetractionHandle) {
		StringBuilder builder = new StringBuilder("Interaction Received: ");

//		if (interactionClass == HandlersHelper
//				.getInteractionHandleByName("InteractionRoot.Finish")) {
//			builder.append("Odebrano interakcję kończącą.");
//			running = false;
//		}

		log(builder.toString());
	}

    public void timeAdvanceGrant( LogicalTime theTime )
    {
        this.federateTime = convertTime( theTime );
        this.isAdvancing = false;
    }

    private double convertTime( LogicalTime logicalTime )
    {
        // PORTICO SPECIFIC!!
        return ((DoubleTime)logicalTime).getTime();
    }

	private void log(String message) {
		System.out.println("StatisticsAmbassador: " + message);
	}

	public void reflectAttributeValues(int theObject,
			ReflectedAttributes theAttributes, byte[] tag) {
		reflectAttributeValues(theObject, theAttributes, tag, null, null);
	}

	public void reflectAttributeValues(int theObject,
			ReflectedAttributes theAttributes, byte[] tag, LogicalTime theTime,
			EventRetractionHandle retractionHandle) {
		StringBuilder builder = new StringBuilder("Reflection for object:");

		builder.append(" handle=" + theObject);
//		builder.append(", tag=" + EncodingHelpers.decodeString(tag));

		// print the attribute information
		builder.append(", attributeCount=" + theAttributes.size());
		builder.append("\n");
		for (int i = 0; i < theAttributes.size(); i++) {
			try {
				// print the attibute handle
				builder.append("\tattributeHandle=");
				builder.append(theAttributes.getAttributeHandle(i));
				// print the attribute value
				builder.append(", currentQueue=");
				builder.append(EncodingHelpers.decodeString(theAttributes
                        .getValue(i)));
                builder.append(", time=");
                builder.append(theTime);
				builder.append("\n");
			} catch (ArrayIndexOutOfBounds aioob) {
				// won't happen
			}
		}

		log(builder.toString());
	}

    @Override
    public void discoverObjectInstance(int theObject, int theObjectClass, String objectName) throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {
        System.out.println("Pojawil sie nowy obiekt typu SimObject");
    }
}
