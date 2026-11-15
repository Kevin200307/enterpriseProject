# syntax=docker/dockerfile:1
FROM python:3.12.10-slim
WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8080

RUN groupadd --system assistant && useradd --system --gid assistant assistant

COPY services/ai-assistant-service/pyproject.toml ./
COPY services/ai-assistant-service/app ./app
COPY services/ai-assistant-service/migrations ./migrations
COPY services/ai-assistant-service/alembic.ini ./
RUN pip install --no-cache-dir .

USER assistant:assistant
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD python -c "import json,urllib.request; assert json.load(urllib.request.urlopen('http://localhost:8080/actuator/health'))['status']=='UP'"

ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
