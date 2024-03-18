pipeline {
    agent any
    
    tools {
        nodejs 'NodeJs'
    }
    
    environment {
        SCANNER_HOME = tool 'sonar-scanner'
        DOCKERHUB_CREDENTIALS = credentials('Docker_hub')
    }
    
    stages {
        stage('Clean workspace') {
            steps {
                cleanWs()
            }
        }
        
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/curlsysolange/reddit-clone-k8s.git'
            }
        }
        
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('sonar-server') {
                    sh "$SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=reddit-clone-k8s -Dsonar.projectKey=reddit-clone-k8s"
                }
            }
        }
        
        stage('Quality Gate') {
            steps {
                script {
                    waitForQualityGate abortPipeline: false, credentialsId: 'Sonar-Token'
                }
            }
        }
        
        stage('NPM') {
            steps {
                sh 'npm install'
            }
        }
      /*
        stage('OWASP FILE SCAN') {
            steps {
                dependencyCheck additionalArguments: '--scan ./ --disableYarnAudit --disableNodeAudit --nvdApiKey 4bdf4acc-8eae-45c1-bfc4-844d549be812', odcInstallation: 'DP'
                dependencyCheckPublisher pattern: '**
            }
        }
      */
        stage('Trivy File Scan') {
            steps {
                script {
                    sh '/usr/local/bin/trivy fs . > trivy_result.txt'
                }
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
                    sh 'docker build -t curlsysolange/reddit-clone-k8s:latest .' 
                    echo "Image Build Successfully"
                }
            }
        }
        
        stage('Docker Push') {
            steps {
                script {
                    sh 'docker push curlsysolange/reddit-clone-k8s:latest'
                    echo "Push Image to Registry"
                }
            }
        }
        
        stage('Trivy Image Scan') {
            steps {
                script {
                    sh '/usr/local/bin/trivy image curlsysolange/reddit-clone-k8s:latest > trivy_image_result.txt'
                    sh 'pwd'
                }
            }
        }
        
        stage('Containerization Deployment') {
            steps {
                script {
                    def containername = 'reddit-clone-k8s'
                    def isRunning = sh(script: "docker ps -a | grep ${containername}", returnStatus: true)
                    if (isRunning == 0) {
                        sh "docker stop ${containername}"
                        sh "docker rm ${containername}"
                    }
                    sh "docker run -d -p 3000:3000 --name ${containername} curlsysolange/reddit-clone-k8s:latest"
                }
            }
        }
        
      stage('Deploy to Kubernetes') {
            steps {
                withKubeConfig([credentialsId: 'kubeconfig']) {
                    sh 'kubectl apply -f deployment.yml'
                    sh 'kubectl apply -f service.yml'
                }
            }
      }
        stage('cluster Scan') {
            steps {
                script {
                    // Run Trivy vulnerability scan on your container images
                    sh 'trivy image --format=json reddit-clone-k8s:latest > vulnerabilities.json'
                }
                // Publish the results as an artifact
                archiveArtifacts artifacts: 'vulnerabilities.json', onlyIfSuccessful: false
            }
        }
   }
}
