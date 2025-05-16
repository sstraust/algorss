import requests
import os
API_URL = "https://w5qsbqbou9x5ja17.us-east-1.aws.endpoints.huggingface.cloud"
HUGGINGFACE_HEADERS = {
	"Accept" : "application/json",
	"Authorization": f"Bearer {os.environ.get('HUGGINGFACE_API_TOKEN')}",
	"Content-Type": "application/json" 
}

def query_huggingface(sentence):
    response = requests.post(API_URL, headers=HUGGINGFACE_HEADERS,
                             json={"inputs": sentence,
                                   "truncate": True,
                                   "parameters" : {}})
    return response.json()[0]
