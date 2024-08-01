from typing import Set
from fastapi import FastAPI, Request
from pydantic import BaseModel
import httpx

app = FastAPI()

# In-memory list to store registered destinations
destinations: Set[str] = set()

# TODO: Get existing destinations, i.e. deployments and preview deployments -> Ask in Coolify Discord
# For now each server has to register itself

class Destination(BaseModel):
  url: str


@app.post("/register")
def register_destination(destination: Destination):
  destinations.add(destination.url)
  return { "message": "Destination registered successfully" }


@app.post("/webhook")
async def send_webhook(request: Request):
  payload = await request.json()
  failed_destinations = []
    
  async with httpx.AsyncClient() as client:
    for destination in destinations:
      try:
        await client.post(destination, json=payload)
      except:
        failed_destinations.append(destination)

  for destination in failed_destinations:
    destinations.remove(destination)

  return { 
    "message": "Webhook sent successfully",
    "failed": len(failed_destinations),
    "success_destinations": len(destinations),
  }
