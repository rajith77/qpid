package org.apache.qpid.address;

import java.util.List;

import junit.framework.TestCase;

public class AddressParsing extends TestCase
{
	public void testDefaults() throws Exception
	{
		Address addr = Address.parse("foo");
		Node node = addr.getNode();
		assertEquals("Incorrect create policy", AddressPolicy.NEVER, node.getCreatePolicy());
		assertEquals("Incorrect assert policy", AddressPolicy.NEVER, node.getAssertPolicy());
		assertEquals("Incorrect delete policy", AddressPolicy.NEVER, node.getDeletePolicy());
		
		assertFalse("Incorrect parsing of durable", node.isDurable());
		assertFalse("Incorrect parsing of auto-delete", node.isAutoDelete());
		assertFalse("Incorrect parsing of exclusive", node.isExclusive());
		
		assertEquals("Incorrect exchange type", "topic", node.getExchangeType());
		assertEquals("Incorrect alternate-exchange", null, node.getAlternateExchange());
		
		assertEquals("Error retrieving args", 0, node.getDeclareArgs().size());
		assertEquals("Error retrieving bindings", 0, node.getBindings());
	}

	public void testNodeProperties() throws Exception
	{
		// A semantically incorrect address used as a way of testing for all available properties.
		String str = "ADDR:my-queue/hello; " +
                "{" + 
                      "create: sender, " +
                      "assert: always, " +
                      "delete: receiver, " +
                      "node: " + 
                      "{" + 
                           "durable: true ," +
                           "x-declare: " +
                           "{" + 
                               "exclusive: true," +
                               "auto-delete: true," +
                               "type: direct" +
                               "alternate-exchange: amq.direct" + 
                               "arguments: {" +  
                                  "'qpid.max_size': 1000," +
                                  "'qpid.max_count': 100" +
                               "}" + 
                            "}, " +   
                            "x-bindings: [{exchange : 'amq.direct', key : test}, " + 
                                         "{exchange : 'amq.fanout'}," +
                                         "{exchange: 'amq.match', arguments: {x-match: any, dep: sales, loc: CA}}," +
                                         "{exchange : 'amq.topic', key : 'a.#'}" +
                                        "]," + 
                               
                      "}" +
                "}";
		
		Address addr = Address.parse(str);
		assertEquals("Incorrect address name", "my-queue", addr.getName());
		assertEquals("Incorrect address subject", "hello", addr.getSubject());
		
		Node node = addr.getNode();
		assertEquals("Incorrect create policy", AddressPolicy.SENDER, node.getCreatePolicy());
		assertEquals("Incorrect assert policy", AddressPolicy.ALWAYS, node.getAssertPolicy());
		assertEquals("Incorrect delete policy", AddressPolicy.RECEIVER, node.getDeletePolicy());
		
		assertTrue("Incorrect parsing of durable", node.isDurable());
		assertTrue("Incorrect parsing of auto-delete", node.isAutoDelete());
		assertTrue("Incorrect parsing of exclusive", node.isExclusive());
		
		assertEquals("Incorrect exchange type", "direct", node.getExchangeType());
		assertEquals("Incorrect alternate-exchange", "amq.direct", node.getAlternateExchange());
		
		assertEquals("qpid.max_size was missing from args", 1000, node.getDeclareArgs().get("qpid.max_size"));
		assertEquals("qpid.max_count was missing from args", 100, node.getDeclareArgs().get("qpid.max_count"));
		
		List<Binding> bindings = node.getBindings();
		assertEquals("Incorrect binding count",4, bindings.size());
	}

	public void testLinkProperties() throws Exception
	{
		// A semantically incorrect address used as a way of testing for all available properties.
		String str = "ADDR:foo/bar; " +
                "{" + 
                      "create: receiver, " +
                      "assert: never, " +
                      "delete: sender, " +
                      "link: " + 
                      "{" + 
                           "name: my-sub," +
                           "durable: true," +
                           "capacity : {sender: 10, receiver 1000}, " +
                           "producer-sync-timeout : 5, " + 
                           "reliability : unreliable" + 
                           "x-declare: " +
                           "{" + 
                               "exclusive: true," +
                               "auto-delete: true," +
                               "type: direct" +
                               "alternate-exchange: amq.direct" + 
                               "arguments: {" +  
                                  "'qpid.max_size': 1000," +
                                  "'qpid.max_count': 100" +
                               "}" + 
                            "}, " +   
                            "x-bindings: [{exchange : 'amq.direct', key : test}, " + 
                                         "{exchange : 'amq.fanout'}," +
                                         "{exchange: 'amq.match', arguments: {x-match: any, dep: sales, loc: CA}}," +
                                         "{exchange : 'amq.topic', key : 'a.#'}" +
                                        "]," + 
                               
                      "}" +
                "}";
		
		Address addr = Address.parse(str);
		assertEquals("Incorrect address name", "my-queue", addr.getName());
		assertEquals("Incorrect address subject", "hello", addr.getSubject());

		Node node = addr.getNode();
		assertEquals("Incorrect create policy", AddressPolicy.SENDER, node.getCreatePolicy());
		assertEquals("Incorrect assert policy", AddressPolicy.ALWAYS, node.getAssertPolicy());
		assertEquals("Incorrect delete policy", AddressPolicy.RECEIVER, node.getDeletePolicy());
		
		Link link = addr.getLink();
		assertTrue("Incorrect parsing of durable", link.isDurable());
		assertTrue("Incorrect parsing of auto-delete", node.isAutoDelete());
		assertTrue("Incorrect parsing of exclusive", node.isExclusive());
		
		assertEquals("Incorrect exchange type", "direct", node.getExchangeType());
		assertEquals("Incorrect alternate-exchange", "amq.direct", node.getAlternateExchange());
		
		assertEquals("qpid.max_size was missing from args", 1000, node.getDeclareArgs().get("qpid.max_size"));
		assertEquals("qpid.max_count was missing from args", 100, node.getDeclareArgs().get("qpid.max_count"));
		
		List<Binding> bindings = node.getBindings();
		assertEquals("Incorrect binding count",4, bindings.size());
	}
}