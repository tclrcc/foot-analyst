# Foot Analyst - Backend API

Application Spring Boot d'analyse prédictive de matchs de football basée sur des statistiques avancées (xG, Forme, Classement).

## 🏗 Architecture
- **Language:** Java 21
- **Framework:** Spring Boot 3
- **Database:** PostgreSQL
- **Architecture:** Layered Architecture (Controller -> Service -> Repository)

## 🚀 Pré-requis
1. Java 21 JDK installé
2. PostgreSQL running on localhost:5432
3. Maven

## 🛠 Installation
1. Cloner le repo
2. Configurer la BDD :
   ```bash
   cp src/main/resources/application-example.properties src/main/resources/application.properties
   # Editer le fichier avec vos credentials
