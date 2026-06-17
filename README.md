# ChatApp
A real-time desktop chat application built with Java Swing and MySQL.

# Features

User registration and login
Real-time messaging between users (polls every 2 seconds)
Clean dark-themed UI with chat bubbles
Message history stored in MySQL
Multi-user support — chat with anyone registered on the same database


# Requirements

Java 11 or higher (uses text blocks)
MySQL 8.0 or higher
MySQL Connector/J JAR on your classpath


# Setup
1. Start MySQL and make sure it's running on port 3306.
2. Edit the credentials at the top of ChatApp.java if needed:
javastatic final String DB_USER = "root";
static final String DB_PASS = "12345678";
3. Compile, making sure the MySQL connector JAR is included:
bashjavac -cp .;mysql-connector-j-x.x.x.jar ChatApp.java   # Windows
javac -cp .:mysql-connector-j-x.x.x.jar ChatApp.java   # Mac/Linux
4. Run:
bashjava -cp .;mysql-connector-j-x.x.x.jar ChatApp   # Windows
java -cp .:mysql-connector-j-x.x.x.jar ChatApp   # Mac/Linux
The database and tables are created automatically on first run.

# How to Use

Launch the app — a login window appears
Sign up with a username and password
Open a second instance (or have a friend connect to the same DB) and sign up with a different username
Select a contact from the left sidebar and start chatting


# Project Structure
ChatApp.java          # Everything in one file
├── ChatApp           # Entry point, DB credentials
├── DB                # All database logic (connect, queries)
├── User              # Simple user data class
├── Message           # Simple message data class
├── LoginFrame        # Login / Sign Up window
├── ChatFrame         # Main chat window
└── RoundedBorder     # Custom rounded bubble border

# Notes

Passwords are stored in plain text — this is a demo project, not production-ready
The app polls the database every 2 seconds to check for new messages
Multiple users can run the app simultaneously as long as they connect to the same MySQL instance
