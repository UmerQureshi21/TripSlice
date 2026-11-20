FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
# Don't run as root
RUN useradd --system --uid 10001 app
USER app
ENV PORT=8000
EXPOSE 8000
# One socket for IPv4 and IPv6: Railway's health check uses IPv4, its private network IPv6
CMD ["python", "-m", "app.serve"]
