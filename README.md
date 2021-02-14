## Description

This is a simple Zendesk ticket loader.

It periodically loads batches of tickets by clients in parallel and sends them in a stream for further processing. 

## Usage

Build:
```
./gradlew build
```
Launch:
```
./gradlew run
```
Add client and start loading tickets from current moment:
```
PUT http://localhost:8080/client
{
    "id": "<client-id>",
    "token": "<zendesk-access-token>"
}
```
Add client and start loading tickets from a specific start time:
```
PUT http://localhost:8080/client
{
    "id": "<client-id>",
    "token": "<zendesk-access-token>",
    "lastUpdateTime": <start-time>
}
```
Add client and start loading tickets with a specific cursor:
```
PUT http://localhost:8080/client
{
    "id": "<client-id>",
    "token": "<zendesk-access-token>",
    "cursor": "<cursor>"
}
```
Get lateness of a client stream in seconds (how far behind real time it is):
```
GET http://localhost:8080/client/<client-id>/lateness
```
