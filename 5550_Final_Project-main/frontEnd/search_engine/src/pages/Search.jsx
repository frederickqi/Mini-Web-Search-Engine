
import React from 'react';
import { AudioOutlined } from '@ant-design/icons';
import { Input, Space, Image, Row, Col } from 'antd';
import logo from "../images/Logo.jpg"
import { useNavigate } from 'react-router-dom'
import "./Search.css"



export default function Search (){


const { Search } = Input;
const suffix = (
  <AudioOutlined
    style={{
      fontSize: 16,
      color: 'ffffff',
    }}
  />
);
// const onSearch = (value) => console.log(value);
const navigate = useNavigate()
const onSearch = (value) => {
    console.log(value);
    localStorage.setItem("mainInput", value)
    navigate('/results');
}

    return (
        <Row id = "SearchPage" className='popup' align = "center">
            <Col span = {10}>
                <Row align = "center">
                    <Image 
                            height = {120}
                            width ={400}
                            src = {logo}
                    />
                </Row>
                <Row align = "center">
                    <Search
                        // allowClear
                        size = "large"
                        placeholder="input search text"
                        onSearch={onSearch}
                        suffix={suffix}
                    />
                </Row>
            </Col>
        </Row>
    )

}
