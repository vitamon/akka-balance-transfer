CAS+blockchain solution is based on Compare and Set pattern and specially calculated transaction ids. 
This approach heavily relies on database with ACID guarantees (I'd recommend Google Spanner as it "offers both strong consistency and horizontal scalability" [https://cloud.google.com/spanner/]), 
but does not require actors, just plain futures, while solving the deadlock problem naturally. 

1. We need a database which provides atomic write(list) and unique index for transactionId.
   So it throws if the key already exists.

2. We calculate transaction Id as the hash of previous transaction object.
   i.e. txId = sha1(lastEntry.toString). For the first transaction in the list txId is just a random uuid.
   
   So the hash of previous entry is the id of the next one (aka blockchain).
   It allows to easily check whether this entry is last or not.
 
3. Implement Compare and Set pattern using the db:
    - load two entries
    - prepare the two new entries with transaction ids equal to hashes of the previous entries
    - try to save the both entries into the db
    - if transaction succeeds -- we are good
    - if by the time of the save any of the accounts was updated, we will have the transaction ids already in the db,
      so db will throw DuplicateKeyException. So we should just re-attempt the transaction from the first step.
      
Implementation:
Scala, Akka-Http for REST api, in-memory concurrent hashmap to emulate persistence

To run the server:
`sbt run`

To run the tests:
`sbt test`
