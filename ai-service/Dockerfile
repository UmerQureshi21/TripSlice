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
# "::" listens on IPv6 and IPv4, Railway's private network has used IPv6
CMD ["sh", "-c", "uvicorn app.main:app --host :: --port ${PORT} --proxy-headers"]
