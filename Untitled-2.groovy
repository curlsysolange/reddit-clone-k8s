Abby, [2/29/2024 3:51 PM]
pipeline {
    agent any
    
    tools {
        nodejs 'node21'
    }
    
    environment {
        SCANNER_HOME = tool 'sonar-scanner'
        DOCKERHUB_CREDENTIALS = credentials('DockerHubCred')
        SEMGREP_APP_TOKEN = credentials('SEMGREP_APP_TOKEN')
        KUBE_CONFIG = credentials('KubeCred')
    }
    
    stages {
        stage('Clean Workspace') { 
            steps {
                cleanWs()
            }
        }
        
        stage('Checkout from Git') {
            steps {
                git branch: 'main', url: 'https://github.com/Abbyabiola/Netflix-clone1.git'
            }
        }
       
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('sonar-server') {
                    sh "$SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=Netflix -Dsonar.projectKey=Netflix"
                }
            }
        }
        
        stage('Install Dependencies') {
            steps {
                sh "npm install"
            }
        }
        
        stage('OWASP FILE SCAN') {
            steps {
                dependencyCheck additionalArguments: '--scan ./ --disableYarnAudit --disableNodeAudit --nvdApiKey 4bdf4acc-8eae-45c1-bfc4-844d549be812', odcInstallation: 'DP'
                dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
            }
        }
        
        stage('TRIVY FS SCAN') {
            steps {
                sh "trivy fs . > trivyfs.txt"
            }
        }
        
        stage('Login to DockerHUB') {
            steps {
                script {
                    sh "echo $DOCKERHUB_CREDENTIALS_PSW | docker login -u $DOCKERHUB_CREDENTIALS_USR --password-stdin"
                    echo 'Login Succeeded'
                }
            }
        }
        
        stage('Docker Build') {
            steps {
                script {
                    sh 'docker build --build-arg TMDB_V3_API_KEY=345d9a81ee2d3d424df458e2b3253027 -t abimbola1981/netflix:latest .'
                    echo "Image Build Successfully"
                }
            }
        }
        
        stage('Semgrep-Scan') {
            steps {
                script {
                    try {
                        // Pull the Semgrep Docker image
                        sh 'docker pull returntocorp/semgrep'
                        // Run Semgrep scan within the Docker container
                        sh ''' docker run \
                            -e SEMGREP_APP_TOKEN=$SEMGREP_APP_TOKEN \
                            -v "$(pwd):/var/lib/jenkins/workspace/amazonproject2" \
                            -w "/var/lib/jenkins/workspace/amazonproject2" \
                            returntocorp/semgrep semgrep ci '''
                    } catch (Exception e) {
                        echo "Failed to execute Semgrep scan: ${e.message}"
                        currentBuild.result = 'FAILURE'
                    }
                }
            }
        }

stage('Trivy image Scan') {
            steps {
                script {
                    sh 'trivy fs . > trivy_result.txt'  // Assuming Trivy is properly installed
                }
            }
        }
        
        stage('Docker Push') {
            steps {
                script {
                    sh 'docker push abimbola1981/netflix:latest'
                    echo "Push Image to Registry"
                }
            }
        }
        
        stage('Containerization Deployment') {
            steps {
                script {
                    try {
                        def containername = 'netflix'
                        def isRunning = sh(script: "docker ps -a | grep ${containername}", returnStatus: true)
                        if (isRunning == 0) {
                            sh "docker stop ${containername}"
                            sh "docker rm ${containername}"
                        }
                        sh "docker run -d -p 7080:80 --name ${containername} abimbola1981/netflix:latest"
                    } catch (Exception e) {
                        echo "Failed to deploy container: ${e.message}"
                    }
                }
            }
        }
        
        
    }
}