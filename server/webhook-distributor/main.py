from typing import Set
from fastapi import FastAPI, Request, status
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


@app.post("/")
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


class HealthCheck(BaseModel):
    status: str = "OK"


@app.get(
    "/health",
    tags=["healthcheck"],
    summary="Perform a Health Check",
    response_description="Return HTTP Status Code 200 (OK)",
    status_code=status.HTTP_200_OK,
    response_model=HealthCheck,
)
def get_health() -> HealthCheck:
    return HealthCheck(status="OK")