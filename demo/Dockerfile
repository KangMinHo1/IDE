# 베이스 이미지: Ubuntu 22.04
FROM ubuntu:22.04

# 환경변수 설정 (상호작용 방지)
ENV DEBIAN_FRONTEND=noninteractive

# 1. 기본 유틸리티 및 언어 설치 (Java, Python, Node.js, C++)
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    gnupg \
    ca-certificates \
    build-essential \
    python3 \
    openjdk-17-jdk \
    nodejs \
    npm

# 2. 작업 폴더 생성
WORKDIR /app

# 3. 기본 명령어
CMD ["/bin/bash"]

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# [추가] 보안을 위해 'ideuser'라는 계정 생성
RUN useradd -m -d /home/ideuser -s /bin/bash ideuser

# 작업 폴더 권한 주기
RUN chown -R ideuser:ideuser /app

# 이제부터 루트 권한 박탈
USER ideuser
WORKDIR /app