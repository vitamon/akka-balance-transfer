This project is a study to implement balance transfer transaction using Akka actors.
I wanted to keep it as simple as possible, so I don't use Akka Persistence, although I'd consider it for full fledged project.

Main ideas:

1. Transaction Log is the only source of truth. All transactions on accounts are appended to the Log. 
   The underlying db must support atomic save(list) operation.
   This guarantees failover of the system. If the node fails, it reloads its state from the (possibly distributed) event log.
    
2. All operations for the given account Id are performed by the dedicated AccountHandlerActor. 
   The actor keeps the state in sync with the Log. 
   This guarantees that
   - all operations would be processed in the required order
   - we have enough amount of money on the account to conduct the current transaction.
   - other operations will be postponed (stash-ed) until the current transaction is completed
   
   AccountHandlerActors are created on the fly when needed and then removed after certain period of inactivity.

3. (optional) Transaction id is calculated using the hash of the entry. 
   Previous transaction Id is stored in the current entry (aka blockchain). 
   This way we can validate that the data in the Log was not altered retroactively.

Implementation:
Scala, Akka, Akka-Http for REST api, in-memory concurrent hashmap to emulate persistence

To run the server:
`sbt run`

To run the tests:
`sbt test`
