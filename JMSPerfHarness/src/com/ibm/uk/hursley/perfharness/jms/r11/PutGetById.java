/********************************************************* {COPYRIGHT-TOP} ***
* Copyright 2016 IBM Corporation
*
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the MIT License
* which accompanies this distribution, and is available at
* http://opensource.org/licenses/MIT
********************************************************** {COPYRIGHT-END} **/
package com.ibm.uk.hursley.perfharness.jms.r11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import com.ibm.uk.hursley.perfharness.Config;
import com.ibm.uk.hursley.perfharness.Log;
import com.ibm.uk.hursley.perfharness.WorkerThread;

/**
 * Consumes messages only.  Currently this class, although JMS 1.1 compliant, is only coded to accept
 * Queue-domain messages.  Use the Subscriber class for topic-domain messages. 
 */
public class PutGetById extends JMS11WorkerThread implements WorkerThread.Paceable, MessageListener {

	@SuppressWarnings("unused")
	private static final String c = com.ibm.uk.hursley.perfharness.Copyright.COPYRIGHT; // IGNORE compiler warning

    Message inMessage = null;
    List<String> msgIdList = Collections.synchronizedList(new ArrayList<String>());
    int nextId = 0;
	   
    public static void registerConfig() {
		Config.registerSelf( PutGetById.class );
	}    
    
    /**
     * Constructor for JMSClientThread.
     * @param name
     */
    public PutGetById(String name) {
        super( name );
    }

    protected void buildJMSResources() throws Exception {
    	
    	super.buildJMSResources();
    	// send messages
    	Message outMessage = null;
    	String correlID = null;
		outMessage=msgFactory.createMessage( session, getName(), 0 );
		
        if ( Config.parms.getBoolean( "co" ) ) {
        	correlID = msgFactory.setJMSCorrelationID( this, outMessage );
        }    
    	
        // Open queues
        if ( destProducer == null ) {
        	destProducer = jmsProvider.lookupQueue( destFactory.generateDestination( getThreadNum() ), session ).destination;
        }
        
        Log.logger.log(Level.FINE, "Creating sender on "
                + getDestinationName(destProducer)
                + (correlID == null ? "" : (" with correlId " + correlID)));
        messageProducer = session.createProducer( destProducer ); 
        
    	String rq = Config.parms.getString("rq");
    	if (rq != null && !rq.equals("")) {
    		//Set the reply to field even though we don't get these messages
    		outMessage.setJMSReplyTo(jmsProvider.lookupQueue( rq, session ).destination);
    	}
    	
    	sendMessages(outMessage, msgIdList);
    	Collections.shuffle(msgIdList);
    	
    	// build consumer
        if ( destConsumer == null ) {
        	destConsumer = jmsProvider.lookupQueue( destFactory.generateDestination( getThreadNum() ), session ).destination;
        }
        
        //String selector = null;
        //Log.logger.log(Level.FINE,"Creating receiver on {0} selector:{1}", new Object[] { getDestinationName(destConsumer), selector });
    	
    }
    
    public void run() {
    	
    	MessageListener ml = Config.parms.getBoolean("as")?this:null;
    	run( this, ml ); // call superclass generic method.
        
    } // End public void run()

	/* (non-Javadoc)
	 * @see com.ibm.uk.hursley.perfharness.WorkerThread.Paceable#oneIteration()
	 */
	public boolean oneIteration() throws Exception {
		
		if(nextId>=msgIdList.size()) {
	        Log.logger.log(Level.WARNING, 
	        		"Number of messages put in the queue is not big enough, no more message available for dequeuing.");
		}
		String msgId = msgIdList.get(nextId++);
		//System.out.println("get ID: "+msgId);
		messageConsumer = session.createConsumer( destConsumer, "JMSMessageID = '"+ msgId+ "'" );
		if( (inMessage=messageConsumer.receive( timeout ))!=null ) {
			if ( transacted && (getIterations()+1)%commitCount==0 ) session.commit();
			incIterations();
		}
		if(messageConsumer!=null) messageConsumer.close();
		
		return true;
	}

	/* (non-Javadoc)
	 * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
	 */
	public void onMessage(Message arg0) {
		if ( transacted && (getIterations()+1)%commitCount==0 ) {
			try {
				session.commit();
			} catch (JMSException je) {
				handleException(je);
			}
		}
		incIterations();		
	}
	
	private void sendMessages(Message outMessage, List<String> msgIdList) throws JMSException {
		int numToPut = 1000;
		if (Config.parms.containsKey("nput")) {
			numToPut = Config.parms.getInt("nput");
		}
		while(numToPut-->0) {
			messageProducer.send(outMessage, deliveryMode, priority, expiry );
			msgIdList.add(outMessage.getJMSMessageID());
			//System.out.println("put ID: "+outMessage.getJMSMessageID());
		}
	}
          
}