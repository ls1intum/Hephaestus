import requests
from langchain_core.tools import tool

from app.logger import logger


@tool
def get_weather(latitude: float, longitude: float) -> dict:
    """Get the current weather at a specific location.

    Args:
        latitude: The latitude coordinate of the location
        longitude: The longitude coordinate of the location

    Returns:
        A dictionary containing current weather information including temperature,
        humidity, wind speed, and weather conditions.
    """
    try:
        url = f"https://api.open-meteo.com/v1/forecast?latitude={latitude}&longitude={longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,showers,snowfall,weather_code,cloud_cover,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m&hourly=temperature_2m,relative_humidity_2m,precipitation_probability&daily=sunrise,sunset,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"

        response = requests.get(url, timeout=10)
        response.raise_for_status()
        data = response.json()

        # Extract and format the most relevant information
        current = data.get("current", {})
        daily = data.get("daily", {})

        formatted_weather = {
            "location": {
                "latitude": latitude,
                "longitude": longitude,
                "timezone": data.get("timezone", "Unknown"),
            },
            "current": {
                "temperature": current.get("temperature_2m"),
                "temperature_unit": data.get("current_units", {}).get(
                    "temperature_2m", "°C"
                ),
                "feels_like": current.get("apparent_temperature"),
                "humidity": current.get("relative_humidity_2m"),
                "wind_speed": current.get("wind_speed_10m"),
                "wind_direction": current.get("wind_direction_10m"),
                "pressure": current.get("pressure_msl"),
                "cloud_cover": current.get("cloud_cover"),
                "precipitation": current.get("precipitation", 0),
                "weather_code": current.get("weather_code"),
                "is_day": current.get("is_day", 1) == 1,
            },
            "daily": {
                "sunrise": daily.get("sunrise", [None])[0],
                "sunset": daily.get("sunset", [None])[0],
                "temperature_max": daily.get("temperature_2m_max", [None])[0],
                "temperature_min": daily.get("temperature_2m_min", [None])[0],
            },
            "timestamp": current.get("time"),
        }

        return formatted_weather

    except requests.RequestException as e:
        logger.error(f"Error fetching weather data: {e}")
        return {"error": f"Failed to fetch weather data: {str(e)}"}
    except Exception as e:
        logger.error(f"Unexpected error in get_weather: {e}")
        return {"error": f"Unexpected error: {str(e)}"}
