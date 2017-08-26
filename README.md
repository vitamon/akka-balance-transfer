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

----------------------------

Another, perhaps even simpler approach, which doesn't require actors, just plain futures,
while solving deadlock problem naturally.

1. We need a database which provides atomic write(list) and unique index by transactionId.
   So it throws if the key already exists.

2. We build transaction Id which is the hash of previous transaction object.
   i.e. txId = sha1(lastEntry.toString). For the first transaction in the list txId is just a random uuid.
 
3. Implement Compare and Set pattern using the database:
    - load two entries
    - prepare two new entries with transaction ids equal to hashes of the previous entries
    - try to save both entries into the db
    - if transaction succeeds -- we are good
    - if by the time of the save any of the accounts was updated, we will have the transaction ids already in the db,
      so it will throw DuplicateKeyException. So we should just start the transaction from the beginning.
      
