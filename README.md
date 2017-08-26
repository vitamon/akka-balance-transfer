This project is a study to implement balance transfer transaction.

The implementation on the master branch is based on Akka actors and can work with eventually consistent databases
(if we keep the account actors unique across the system). So it is easy to scale using.

Another approach to implement balance transfer transaction could be using Compare and Set pattern with specially calculated transaction ids. 
This approach heavily relies on database with ACID guarantees (so not easily scalable), 
but does not require actors, just plain futures, while solving the deadlock problem naturally. 
See the code in the branch here: https://github.com/vitamon/akka-balance-transfer/tree/cas-solution


Akka solution

1. Transaction Log is the only source of truth. All transactions on accounts are appended to the Log. 
   The underlying db must support atomic save(list) operation.
   This guarantees failover of the system. If the node fails, it reloads its state from the (possibly distributed) event log.
    
2. All operations for the given account Id are performed by the unique AccountHandlerActor. 
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
